/*
 * Copyright 2020-2023 RW-HPS Team and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/RW-HPS/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.util.annotations

/**
 * 指定方法是需要迁移。一般来说，这并不会改变IDEA的行为——它只是一个标记，表明指定方法是未完成的。
 * Specifies that the method needs to be migrated.
 * In general, this doesn't change the behavior of the idea - it's just a token that indicates that the specified method is incomplete。
 *
 * @author RW-HPS/Dr
 */
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
@Target(
        AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.EXPRESSION
)
internal annotation class MigrationWaiting(val status: String = "")
