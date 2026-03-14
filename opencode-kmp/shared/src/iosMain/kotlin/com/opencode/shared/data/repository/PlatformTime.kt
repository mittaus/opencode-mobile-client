package com.opencode.shared.data.repository
import platform.Foundation.NSDate
actual fun currentTimeMs(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
