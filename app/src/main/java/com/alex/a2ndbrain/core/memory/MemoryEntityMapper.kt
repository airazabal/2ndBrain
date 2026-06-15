package com.alex.a2ndbrain.core.memory

import com.alex.a2ndbrain.core.domain.Memory

fun MemoryEntity.toDomain() = Memory(
    id = id,
    source = source,
    packageName = packageName,
    title = title,
    content = content,
    deepLink = deepLink,
    isRead = isRead,
    timestamp = timestamp,
    duplicateCount = duplicateCount,
    tags = tags
)
