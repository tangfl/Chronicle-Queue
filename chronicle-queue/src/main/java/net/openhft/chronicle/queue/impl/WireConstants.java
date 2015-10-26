/*
 *     Copyright (C) 2015  higherfrequencytrading.com
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.openhft.chronicle.queue.impl;

import net.openhft.chronicle.bytes.NativeBytes;
import net.openhft.chronicle.core.pool.StringBuilderPool;

public class WireConstants {
    public static final StringBuilderPool SBP = new StringBuilderPool();
    public static final ThreadLocal<NativeBytes> NBP = ThreadLocal.withInitial(NativeBytes::nativeBytes);

    public static final long NO_DATA = 0L;
    public static final long NO_INDEX = -1L;
    public static final long HEADER_OFFSET = 0L;
    public static final long SPB_DATA_HEADER_SIZE = 4L;
}
