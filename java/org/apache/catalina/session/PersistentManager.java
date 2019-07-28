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
package org.apache.catalina.session;

/**
 * Implementation of the <b>Manager</b> interface that makes use of
 * a Store to swap active Sessions to disk. It can be configured to
 * achieve several different goals:
 *
 * <ul>
 * <li>Persist sessions across restarts of the Container</li>
 * <li>Fault tolerance, keep sessions backed up on disk to allow
 *     recovery in the event of unplanned restarts.</li>
 * <li>Limit the number of active sessions kept in memory by
 *     swapping less active sessions out to disk.</li>
 * </ul>
 *
 * @author Kief Morris (kief@kief.com)
 */

//当会话长时间空闲时,会把 Session 信息写入磁盘,从而限制内存中的活动会话数量
// 支持容错,会定期将内存中的 Session 信息备份到磁盘
public final class PersistentManager extends PersistentManagerBase {

    // ----------------------------------------------------- Instance Variables

    /**
     * The descriptive name of this Manager implementation (for logging).
     */
    private static final String name = "PersistentManager";


    // ------------------------------------------------------------- Properties

    @Override
    public String getName() {
        return name;
    }
 }

