package com.chominjungum.service;

import com.chominjungum.domain.Classroom;
import com.chominjungum.domain.Student;
import com.chominjungum.domain.StudentDevice;
import com.chominjungum.repo.ClassroomRepository;
import com.chominjungum.repo.StudentDeviceRepository;
import com.chominjungum.repo.StudentRepository;
import com.chominjungum.web.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassroomService {

    private final ClassroomRepository classrooms;
    private final StudentRepository students;
    private final StudentDeviceRepository devices;
    private final AuthService authService;

    public ClassroomService(
            ClassroomRepository classrooms,
            StudentRepository students,
            StudentDeviceRepository devices,
            AuthService authService) {
        this.classrooms = classrooms;
        this.students = students;
        this.devices = devices;
        this.authService = authService;
    }

    @Transactional
    public Classroom create(UUID teacherId, String name, Integer grade, Integer schoolYear) {
        return classrooms.save(
                new Classroom(teacherId, name, grade, schoolYear, authService.generateJoinCode()));
    }

    public List<Classroom> listByTeacher(UUID teacherId) {
        return classrooms.findByTeacherIdOrderByCreatedAtDesc(teacherId);
    }

    /** 교사 소유가 아닌 학급에 접근하면 거부한다. 업싱크에서도 이 검증을 반드시 거친다. */
    public Classroom getOwned(UUID teacherId, UUID classroomId) {
        Classroom classroom = classrooms.findById(classroomId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "학급을 찾을 수 없습니다."));
        if (!classroom.getTeacherId().equals(teacherId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "다른 교사의 학급입니다.");
        }
        return classroom;
    }

    public List<Student> roster(UUID teacherId, UUID classroomId) {
        getOwned(teacherId, classroomId);
        return students.findByClassroomIdOrderByStudentNoAscDisplayNameAsc(classroomId);
    }

    @Transactional
    public Student addStudent(UUID teacherId, UUID classroomId, String displayName, Integer studentNo) {
        getOwned(teacherId, classroomId);
        String name = displayName == null ? "" : displayName.trim();
        if (name.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "학생 이름을 입력하세요.");
        }
        return students.save(new Student(classroomId, name, studentNo));
    }

    /**
     * 기기 ↔ 명단 연결. 교실 LAN 제출은 익명 기기 ID 로 들어오므로 이 연결이 성적부의 열쇠다.
     * 연결 시점 이전의 제출도 소급 매핑된다(AttemptService).
     */
    @Transactional
    public StudentDevice bindDevice(UUID teacherId, UUID classroomId, String deviceBindingId, UUID studentId) {
        getOwned(teacherId, classroomId);
        Student student = students.findById(studentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "학생을 찾을 수 없습니다."));
        if (!student.getClassroomId().equals(classroomId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "학생이 이 학급 소속이 아닙니다.");
        }
        return devices.save(new StudentDevice(deviceBindingId, studentId));
    }
}
