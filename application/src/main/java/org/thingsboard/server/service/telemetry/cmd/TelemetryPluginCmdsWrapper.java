/**
 * Copyright © 2016-2020 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.service.telemetry.cmd;

import java.util.List;

/**
 * @author Andrew Shvayka
 * 设计的三种类型的指令：
 * 属性查询指令：attrSubCmds
 * 最新数据指令：tsSubCmds
 * 历史数据指令：historyCmds
 */
public class TelemetryPluginCmdsWrapper {

    private List<AttributesSubscriptionCmd> attrSubCmds;

    private List<TimeseriesSubscriptionCmd> tsSubCmds;

    private List<GetHistoryCmd> historyCmds;

    public TelemetryPluginCmdsWrapper() {
        super();
    }

    public List<AttributesSubscriptionCmd> getAttrSubCmds() {
        return attrSubCmds;
    }

    public void setAttrSubCmds(List<AttributesSubscriptionCmd> attrSubCmds) {
        this.attrSubCmds = attrSubCmds;
    }

    public List<TimeseriesSubscriptionCmd> getTsSubCmds() {
        return tsSubCmds;
    }

    public void setTsSubCmds(List<TimeseriesSubscriptionCmd> tsSubCmds) {
        this.tsSubCmds = tsSubCmds;
    }

    public List<GetHistoryCmd> getHistoryCmds() {
        return historyCmds;
    }

    public void setHistoryCmds(List<GetHistoryCmd> historyCmds) {
        this.historyCmds = historyCmds;
    }
}
