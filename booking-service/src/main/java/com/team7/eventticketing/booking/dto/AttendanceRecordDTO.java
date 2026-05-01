package com.team7.eventticketing.booking.dto;

/**
 * DTO for the attendance recording response.
 * satisfying the S3-F11 test scenarios which require returning the attendanceCount.
 */
public class AttendanceRecordDTO {
    private Integer attendanceCount;

    public AttendanceRecordDTO() {}

    public AttendanceRecordDTO(Integer attendanceCount) {
        this.attendanceCount = attendanceCount;
    }

    public Integer getAttendanceCount() {
        return attendanceCount;
    }

    public void setAttendanceCount(Integer attendanceCount) {
        this.attendanceCount = attendanceCount;
    }
}
