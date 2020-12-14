/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.catalina.core;

import org.apache.catalina.*;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.ProtocolHandler;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;

import java.util.concurrent.Executor;

/**
 * <p>
 * A {@link LifecycleListener} that triggers the renewal of threads in Executor
 * pools when a {@link Context} is being stopped to avoid thread-local related
 * memory leaks.
 * </p>
 * <p>
 * Note : active threads will be renewed one by one when they come back to the
 * pool after executing their task, see
 * {@link org.apache.tomcat.util.threads.ThreadPoolExecutor}.afterExecute().
 * </p>
 *
 * This listener must be declared in server.xml to be active.
 *
 */

//threadLocal 内存泄漏处理

/***
 * 在 ThreadLocal 中保存了对象A,而且对象A由 ParallelWebappClassLoader 加载,
 * 那么就可以看成线程引用了对象A。由于 tomcat 中处理请求的是线程池,
 * 意味着该线程会存活很长一段时间。webapp 热加载时,
 * 会重新实例化一个 ParallelWebappClassLoader 对象,
 * 如果线程未销毁,那么旧的 ParallelWebappClassLoader 也无法被回收,导致内存泄露。

 */
public class ThreadLocalLeakPreventionListener implements LifecycleListener,
        ContainerListener {

    private static final Log log =
        LogFactory.getLog(ThreadLocalLeakPreventionListener.class);

    private volatile boolean serverStopping = false;

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);

    /**
     * Listens for {@link LifecycleEvent} for the start of the {@link Server} to
     * initialize itself and then for after_stop events of each {@link Context}.
     */
    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        try {
            Lifecycle lifecycle = event.getLifecycle();
            if (Lifecycle.AFTER_START_EVENT.equals(event.getType()) &&
                    lifecycle instanceof Server) {
                // when the server starts, we register ourself as listener for
                // all context
                // as well as container event listener so that we know when new
                // Context are deployed
                Server server = (Server) lifecycle;
                registerListenersForServer(server);
            }

            if (Lifecycle.BEFORE_STOP_EVENT.equals(event.getType()) &&
                    lifecycle instanceof Server) {
                // Server is shutting down, so thread pools will be shut down so
                // there is no need to clean the threads
                serverStopping = true;
            }

            //ThreadLocal内存泄漏
            // context关闭之后,after_stop
            // 毁线程池内的空闲线程
            if (Lifecycle.AFTER_STOP_EVENT.equals(event.getType()) &&
                    lifecycle instanceof Context) {
                stopIdleThreads((Context) lifecycle);
            }
        } catch (Exception e) {
            String msg =
                sm.getString(
                    "threadLocalLeakPreventionListener.lifecycleEvent.error",
                    event);
            log.error(msg, e);
        }
    }

    @Override
    public void containerEvent(ContainerEvent event) {
        try {
            String type = event.getType();
            if (Container.ADD_CHILD_EVENT.equals(type)) {
                processContainerAddChild(event.getContainer(),
                    (Container) event.getData());
            } else if (Container.REMOVE_CHILD_EVENT.equals(type)) {
                processContainerRemoveChild(event.getContainer(),
                    (Container) event.getData());
            }
        } catch (Exception e) {
            String msg =
                sm.getString(
                    "threadLocalLeakPreventionListener.containerEvent.error",
                    event);
            log.error(msg, e);
        }

    }

    private void registerListenersForServer(Server server) {
        for (Service service : server.findServices()) {
            Engine engine = service.getContainer();
            if (engine != null) {
                engine.addContainerListener(this);
                registerListenersForEngine(engine);
            }
        }

    }

    private void registerListenersForEngine(Engine engine) {
        for (Container hostContainer : engine.findChildren()) {
            Host host = (Host) hostContainer;
            host.addContainerListener(this);
            registerListenersForHost(host);
        }
    }

    private void registerListenersForHost(Host host) {
        for (Container contextContainer : host.findChildren()) {
            Context context = (Context) contextContainer;
            registerContextListener(context);
        }
    }

    private void registerContextListener(Context context) {
        context.addLifecycleListener(this);
    }

    protected void processContainerAddChild(Container parent, Container child) {
        if (log.isDebugEnabled())
            log.debug("Process addChild[parent=" + parent + ",child=" + child +
                "]");

        if (child instanceof Context) {
            registerContextListener((Context) child);
        } else if (child instanceof Engine) {
            registerListenersForEngine((Engine) child);
        } else if (child instanceof Host) {
            registerListenersForHost((Host) child);
        }

    }

    protected void processContainerRemoveChild(Container parent,
        Container child) {

        if (log.isDebugEnabled())
            log.debug("Process removeChild[parent=" + parent + ",child=" +
                child + "]");

        if (child instanceof Context) {
            Context context = (Context) child;
            context.removeLifecycleListener(this);
        } else if (child instanceof Host || child instanceof Engine) {
            child.removeContainerListener(this);
        }
    }

    /**
     * Updates each ThreadPoolExecutor with the current time, which is the time
     * when a context is being stopped.
     *
     * @param context
     *            the context being stopped, used to discover all the Connectors
     *            of its parent Service.
     */
    private void stopIdleThreads(Context context) {
        if (serverStopping) return;

        if (!(context instanceof StandardContext) ||
            !((StandardContext) context).getRenewThreadsWhenStoppingContext()) {
            log.debug("Not renewing threads when the context is stopping. "
                + "It is not configured to do it.");
            return;
        }

        Engine engine = (Engine) context.getParent().getParent();
        Service service = engine.getService();
        Connector[] connectors = service.findConnectors();
        if (connectors != null) {
            for (Connector connector : connectors) {
                //connector的protoHandler
                ProtocolHandler handler = connector.getProtocolHandler();
                Executor executor = null;
                if (handler != null) {//protocolHandler的线程池
                    executor = handler.getExecutor();
                }

                // 销毁线程池 ThreadPoolExecutor
                // 首先将任务队列设为 0,
                // 再设置coreSize为0（会触发线程池内线程的interrupt）
                // 从而销毁空闲的线程
                if (executor instanceof ThreadPoolExecutor) {
                    ThreadPoolExecutor threadPoolExecutor =
                        (ThreadPoolExecutor) executor;
                    threadPoolExecutor.contextStopping();
                } else if (executor instanceof StandardThreadExecutor) {
                    StandardThreadExecutor stdThreadExecutor =
                        (StandardThreadExecutor) executor;
                    stdThreadExecutor.contextStopping();
                }

            }
        }
    }
}
