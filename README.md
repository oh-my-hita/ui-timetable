# HITA Timetable UI (T2)

课表界面插件。只依赖 `plugin-sdk` 与 `TimetableService`，不直接访问 Room/DAO/ICS。

- T1 数据核心：`oh-my-hita/timetable-core`
- 服务契约：`com.stupidtree.hita.api.TimetableService`
- 当前范围：读取当前课表的当前周事件；完整周视图、编辑与课程详情继续移植。
