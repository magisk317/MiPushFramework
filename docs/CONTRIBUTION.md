# Contribution Guide

* 欢迎提交 PR、Issues 帮助这个项目更好。
* 代码规范：Alibaba Java Coding Guidelines / Google Java 编程规范。
* 尽量丰富注释和文档。
* Git commit message 规范：[Angular](https://github.com/angular/angular.js/blob/master/DEVELOPERS.md#-git-commit-guidelines)。
* 构建本项目所需的本地配置文件，参见样例文件 `local.properties.example`。
* 常用维护脚本统一放在 `scripts/` 目录。
* 需要运行 Gradle 时，优先通过 `scripts/with_workspace_gradle_lock.sh` 启动，避免同一工作区并发构建踩坏 KSP/Hilt 生成目录。
* 需要打正式包时，优先使用 `scripts/build_release.sh`。
* 请优先向当前活跃的 `dev` 分支提交 PR；如果维护者明确指定其他分支，再按指定分支提交。
