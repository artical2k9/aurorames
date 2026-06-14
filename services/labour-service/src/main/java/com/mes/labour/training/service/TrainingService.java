package com.mes.labour.training.service;

import com.mes.labour.employee.domain.Employee;
import com.mes.labour.employee.repository.EmployeeRepository;
import com.mes.labour.service.LabourNotFoundException;
import com.mes.labour.service.LabourValidationException;
import com.mes.labour.training.api.dto.TrainingDtos.AttendeeDto;
import com.mes.labour.training.api.dto.TrainingDtos.AttendeeRequest;
import com.mes.labour.training.api.dto.TrainingDtos.CreateTrainingEventRequest;
import com.mes.labour.training.api.dto.TrainingDtos.PatchTrainingEventRequest;
import com.mes.labour.training.api.dto.TrainingDtos.TrainingEventDto;
import com.mes.labour.training.api.dto.TrainingDtos.TrainingHistoryEntryDto;
import com.mes.labour.training.domain.TrainingAttendance;
import com.mes.labour.training.domain.TrainingEvent;
import com.mes.labour.training.domain.TrainingOutcome;
import com.mes.labour.training.repository.TrainingAttendanceRepository;
import com.mes.labour.training.repository.TrainingEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TrainingService {

    private final TrainingEventRepository trainingEventRepository;
    private final TrainingAttendanceRepository trainingAttendanceRepository;
    private final EmployeeRepository employeeRepository;

    public TrainingService(TrainingEventRepository trainingEventRepository,
                           TrainingAttendanceRepository trainingAttendanceRepository,
                           EmployeeRepository employeeRepository) {
        this.trainingEventRepository = trainingEventRepository;
        this.trainingAttendanceRepository = trainingAttendanceRepository;
        this.employeeRepository = employeeRepository;
    }

    public TrainingEventDto create(UUID orgId, CreateTrainingEventRequest req) {
        TrainingEvent event = new TrainingEvent();
        event.setOrgId(orgId);
        event.setTitle(req.getTitle());
        event.setTrainingDate(req.getTrainingDate());
        event.setDurationMinutes(req.getDurationMinutes());
        event.setTrainer(req.getTrainer());
        event.setNotes(req.getNotes());
        if (req.getSkillIds() != null) {
            event.setSkillIds(new HashSet<>(req.getSkillIds()));
        }
        event.setCustomFields(req.getCustomFields());
        TrainingEvent saved = trainingEventRepository.save(event);

        List<TrainingAttendance> attendances = new ArrayList<>();
        for (AttendeeRequest attendee : req.getAttendees()) {
            Employee employee = employeeRepository
                    .findByOrgIdAndId(orgId, attendee.getEmployeeId())
                    .orElseThrow(() -> new LabourValidationException(
                            "Attendee employee not found: " + attendee.getEmployeeId()));
            TrainingAttendance attendance = new TrainingAttendance();
            attendance.setTrainingEvent(saved);
            attendance.setEmployee(employee);
            attendance.setOutcome(parseOutcome(attendee.getOutcome()));
            attendances.add(trainingAttendanceRepository.save(attendance));
        }
        return toDto(saved, attendances);
    }

    @Transactional(readOnly = true)
    public TrainingEventDto get(UUID orgId, UUID eventId) {
        TrainingEvent event = requireEvent(orgId, eventId);
        return toDto(event, trainingAttendanceRepository.findAllForEvent(eventId));
    }

    @Transactional(readOnly = true)
    public Page<TrainingEventDto> list(UUID orgId, Pageable pageable) {
        return trainingEventRepository.findAllByOrgIdOrderByTrainingDateDesc(orgId, pageable)
                .map(event -> toDto(event,
                        trainingAttendanceRepository.findAllForEvent(event.getId())));
    }

    public TrainingEventDto patch(UUID orgId, UUID eventId, PatchTrainingEventRequest req) {
        TrainingEvent event = requireEvent(orgId, eventId);
        if (req.getTitle() != null) {
            event.setTitle(req.getTitle());
        }
        if (req.getTrainingDate() != null) {
            event.setTrainingDate(req.getTrainingDate());
        }
        if (req.getDurationMinutes() != null) {
            event.setDurationMinutes(req.getDurationMinutes());
        }
        if (req.getTrainer() != null) {
            event.setTrainer(req.getTrainer());
        }
        if (req.getNotes() != null) {
            event.setNotes(req.getNotes());
        }
        if (req.getSkillIds() != null) {
            event.setSkillIds(new HashSet<>(req.getSkillIds()));
        }
        if (req.getCustomFields() != null) {
            event.setCustomFields(req.getCustomFields());
        }
        trainingEventRepository.save(event);

        List<TrainingAttendance> attendances =
                trainingAttendanceRepository.findAllForEvent(eventId);
        if (req.getAttendees() != null) {
            for (AttendeeRequest update : req.getAttendees()) {
                TrainingAttendance attendance = attendances.stream()
                        .filter(a -> a.getEmployee().getId().equals(update.getEmployeeId()))
                        .findFirst()
                        .orElseThrow(() -> new LabourValidationException(
                                "No attendance row for employee: " + update.getEmployeeId()));
                attendance.setOutcome(parseOutcome(update.getOutcome()));
                trainingAttendanceRepository.save(attendance);
            }
        }
        return toDto(event, attendances);
    }

    @Transactional(readOnly = true)
    public List<TrainingHistoryEntryDto> historyFor(UUID orgId, UUID employeeId) {
        employeeRepository.findByOrgIdAndId(orgId, employeeId)
                .orElseThrow(() -> new LabourNotFoundException("Employee not found: " + employeeId));
        return trainingAttendanceRepository.findAllForEmployee(orgId, employeeId).stream()
                .map(this::toHistoryEntry)
                .toList();
    }

    /** Supporting-training evidence for a certification: same employee + linked skill. */
    @Transactional(readOnly = true)
    public List<TrainingHistoryEntryDto> supportingTraining(UUID orgId, UUID employeeId,
                                                            UUID skillId) {
        return trainingAttendanceRepository.findAllForEmployee(orgId, employeeId).stream()
                .filter(a -> a.getTrainingEvent().getSkillIds().contains(skillId))
                .map(this::toHistoryEntry)
                .toList();
    }

    private TrainingOutcome parseOutcome(String outcome) {
        if (outcome == null || outcome.isBlank()) {
            return TrainingOutcome.COMPLETED;
        }
        try {
            return TrainingOutcome.valueOf(outcome);
        } catch (IllegalArgumentException e) {
            throw new LabourValidationException("Unknown training outcome: " + outcome);
        }
    }

    private TrainingHistoryEntryDto toHistoryEntry(TrainingAttendance attendance) {
        TrainingEvent event = attendance.getTrainingEvent();
        TrainingHistoryEntryDto dto = new TrainingHistoryEntryDto();
        dto.setTrainingEventId(event.getId());
        dto.setTitle(event.getTitle());
        dto.setTrainingDate(event.getTrainingDate());
        dto.setDurationMinutes(event.getDurationMinutes());
        dto.setTrainer(event.getTrainer());
        dto.setOutcome(attendance.getOutcome().name());
        dto.setSkillIds(List.copyOf(event.getSkillIds()));
        return dto;
    }

    private TrainingEventDto toDto(TrainingEvent event, List<TrainingAttendance> attendances) {
        TrainingEventDto dto = new TrainingEventDto();
        dto.setId(event.getId());
        dto.setTitle(event.getTitle());
        dto.setTrainingDate(event.getTrainingDate());
        dto.setDurationMinutes(event.getDurationMinutes());
        dto.setTrainer(event.getTrainer());
        dto.setNotes(event.getNotes());
        dto.setSkillIds(List.copyOf(event.getSkillIds()));
        dto.setCustomFields(event.getCustomFields());
        dto.setAttendees(attendances.stream().map(a -> {
            AttendeeDto attendee = new AttendeeDto();
            attendee.setId(a.getId());
            attendee.setEmployeeId(a.getEmployee().getId());
            attendee.setEmployeeNumber(a.getEmployee().getEmployeeNumber());
            attendee.setOutcome(a.getOutcome().name());
            return attendee;
        }).toList());
        return dto;
    }

    private TrainingEvent requireEvent(UUID orgId, UUID eventId) {
        return trainingEventRepository.findByOrgIdAndId(orgId, eventId)
                .orElseThrow(() -> new LabourNotFoundException(
                        "Training event not found: " + eventId));
    }
}
