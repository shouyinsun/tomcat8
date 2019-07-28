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

package org.apache.catalina.util;

import org.apache.catalina.Globals;
import org.apache.catalina.JmxEnabled;
import org.apache.catalina.LifecycleException;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.res.StringManager;

import javax.management.*;

/***
 * LifecycleMBeanBase完成了jmx注册的主要逻辑
 * 重写了LifecycleBase的initInternal、destroyInternal方法,用于完成jmx的注册、注销动作
 */
public abstract class LifecycleMBeanBase extends LifecycleBase
        implements JmxEnabled {

    private static final Log log = LogFactory.getLog(LifecycleMBeanBase.class);

    private static final StringManager sm =
        StringManager.getManager("org.apache.catalina.util");


    /* Cache components of the MBean registration. */
    //jmx的域,默认使用Service的name,即"Catalina"
    private String domain = null;
    //用于标识一个MBean的对象名称,也可以根据这个name来查找MBean
    private ObjectName oname = null;
    //jmx的核心组件,提供代理端操作MBean的接口
    //提供了创建、注册、删除MBean的接口,它由MBeanServerFactory创建
    protected MBeanServer mserver = null;

    /**
     * Sub-classes wishing to perform additional initialization should override
     * this method, ensuring that super.initInternal() is the first call in the
     * overriding method.
     */

    /**
     * 为了保证jmx的正常注册和注销,
     * 要求子类在重写initInternal、先调用super.initInternal()
     * **/


    //在initInternal阶段初始化MBeanServer实例
    // 并且把当前实例注册到jmx中
    @Override
    protected void initInternal() throws LifecycleException {

        // If oname is not null then registration has already happened via
        // preRegister().
        if (oname == null) {
            mserver = Registry.getRegistry(null, null).getMBeanServer();

            oname = register(this, getObjectNameKeyProperties());
        }
    }


    /**
     * Sub-classes wishing to perform additional clean-up should override this
     * method, ensuring that super.destroyInternal() is the last call in the
     * overriding method.
     */

    /***
     * 子类覆盖是最后调用super.destroyInternal()
     */


    //destroyInternal阶段则根据ObjectName注销MBean
    @Override
    protected void destroyInternal() throws LifecycleException {
        unregister(oname);
    }


    /**
     * Specify the domain under which this component should be registered. Used
     * with components that cannot (easily) navigate the component hierarchy to
     * determine the correct domain to use.
     */
    @Override
    public final void setDomain(String domain) {
        this.domain = domain;
    }


    /**
     * Obtain the domain under which this component will be / has been
     * registered.
     */
    @Override
    public final String getDomain() {
        if (domain == null) {
            domain = getDomainInternal();
        }

        if (domain == null) {//默认 Catalina
            domain = Globals.DEFAULT_MBEAN_DOMAIN;
        }

        return domain;
    }


    /**
     * Method implemented by sub-classes to identify the domain in which MBeans
     * should be registered.
     *
     * @return  The name of the domain to use to register MBeans.
     */
    protected abstract String getDomainInternal();


    /**
     * Obtain the name under which this component has been registered with JMX.
     */
    @Override
    public final ObjectName getObjectName() {
        return oname;
    }


    /**
     * Allow sub-classes to specify the key properties component of the
     * {@link ObjectName} that will be used to register this component.
     *
     * @return  The string representation of the key properties component of the
     *          desired {@link ObjectName}
     */
    protected abstract String getObjectNameKeyProperties();


    /**
     * Utility method to enable sub-classes to easily register additional
     * components that don't implement {@link JmxEnabled} with an MBean server.
     * <br>
     * Note: This method should only be used once {@link #initInternal()} has
     * been called and before {@link #destroyInternal()} has been called.
     *
     * @param obj                       The object the register
     * @param objectNameKeyProperties   The key properties component of the
     *                                  object name to use to register the
     *                                  object
     *
     * @return  The name used to register the object
     */
    protected final ObjectName register(Object obj,
            String objectNameKeyProperties) {

        // Construct an object name with the right domain
        StringBuilder name = new StringBuilder(getDomain());
        name.append(':');
        name.append(objectNameKeyProperties);

        ObjectName on = null;

        try {
            on = new ObjectName(name.toString());

            Registry.getRegistry(null, null).registerComponent(obj, on, null);
        } catch (MalformedObjectNameException e) {
            log.warn(sm.getString("lifecycleMBeanBase.registerFail", obj, name),
                    e);
        } catch (Exception e) {
            log.warn(sm.getString("lifecycleMBeanBase.registerFail", obj, name),
                    e);
        }

        return on;
    }


    /**
     * Utility method to enable sub-classes to easily unregister additional
     * components that don't implement {@link JmxEnabled} with an MBean server.
     * <br>
     * Note: This method should only be used once {@link #initInternal()} has
     * been called and before {@link #destroyInternal()} has been called.
     *
     * @param on    The name of the component to unregister
     */
    protected final void unregister(ObjectName on) {

        // If null ObjectName, just return without complaint
        if (on == null) {
            return;
        }

        // If the MBeanServer is null, log a warning & return
        if (mserver == null) {
            log.warn(sm.getString("lifecycleMBeanBase.unregisterNoServer", on));
            return;
        }

        try {
            mserver.unregisterMBean(on);
        } catch (MBeanRegistrationException e) {
            log.warn(sm.getString("lifecycleMBeanBase.unregisterFail", on), e);
        } catch (InstanceNotFoundException e) {
            log.warn(sm.getString("lifecycleMBeanBase.unregisterFail", on), e);
        }

    }


    /**
     * Not used - NOOP.
     */
    @Override
    public final void postDeregister() {
        // NOOP
    }


    /**
     * Not used - NOOP.
     */
    @Override
    public final void postRegister(Boolean registrationDone) {
        // NOOP
    }


    /**
     * Not used - NOOP.
     */
    @Override
    public final void preDeregister() throws Exception {
        // NOOP
    }


    /**
     * Allows the object to be registered with an alternative
     * {@link MBeanServer} and/or {@link ObjectName}.
     */
    @Override
    public final ObjectName preRegister(MBeanServer server, ObjectName name)
            throws Exception {

        this.mserver = server;
        this.oname = name;
        this.domain = name.getDomain();

        return oname;
    }

}
