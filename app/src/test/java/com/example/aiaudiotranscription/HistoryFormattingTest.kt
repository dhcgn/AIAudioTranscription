package com.example.aiaudiotranscription

import com.example.aiaudiotranscription.presentation.formatDuration
import com.example.aiaudiotranscription.presentation.formatFileSize
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for history formatting helper functions
 */
class HistoryFormattingTest {
    
    @Test
    fun formatFileSize_zeroBytes_returnsNA() {
        assertEquals("N/A", formatFileSize(0))
    }
    
    @Test
    fun formatFileSize_negativeBytes_returnsNA() {
        assertEquals("N/A", formatFileSize(-100))
    }
    
    @Test
    fun formatFileSize_bytes_returnsCorrectFormat() {
        assertEquals("100.00 B", formatFileSize(100))
        assertEquals("1023.00 B", formatFileSize(1023))
    }
    
    @Test
    fun formatFileSize_kilobytes_returnsCorrectFormat() {
        assertEquals("1.00 KB", formatFileSize(1024))
        assertEquals("10.50 KB", formatFileSize(10752)) // 10.5 * 1024
    }
    
    @Test
    fun formatFileSize_megabytes_returnsCorrectFormat() {
        assertEquals("1.00 MB", formatFileSize(1048576)) // 1024 * 1024
        assertEquals("5.00 MB", formatFileSize(5242880)) // 5 * 1024 * 1024
    }
    
    @Test
    fun formatFileSize_gigabytes_returnsCorrectFormat() {
        assertEquals("1.00 GB", formatFileSize(1073741824)) // 1024 * 1024 * 1024
    }
    
    @Test
    fun formatDuration_zeroSeconds_returnsNA() {
        assertEquals("N/A", formatDuration(0))
    }
    
    @Test
    fun formatDuration_negativeSeconds_returnsNA() {
        assertEquals("N/A", formatDuration(-10))
    }
    
    @Test
    fun formatDuration_seconds_returnsCorrectFormat() {
        assertEquals("5s", formatDuration(5))
        assertEquals("59s", formatDuration(59))
    }
    
    @Test
    fun formatDuration_minutes_returnsCorrectFormat() {
        assertEquals("1:00", formatDuration(60))
        assertEquals("3:30", formatDuration(210)) // 3 * 60 + 30
        assertEquals("59:59", formatDuration(3599))
    }
    
    @Test
    fun formatDuration_hours_returnsCorrectFormat() {
        assertEquals("1:00:00", formatDuration(3600))
        assertEquals("2:30:45", formatDuration(9045)) // 2 * 3600 + 30 * 60 + 45
    }
}
