package com.mes.labour.training.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request/response DTOs for training events. Grouped to keep the package compact. */
public final class TrainingDtos {

    private TrainingDtos() {
    }

    public static class AttendeeRequest {

        @NotNull
        private UUID employeeId;

        private String outcome;

        public UUID getEmployeeId() {
            return employeeId;
        }

        public void setEmployeeId(UUID employeeId) {
            this.employeeId = employeeId;
        }

        public String getOutcome() {
            return outcome;
        }

        public void setOutcome(String outcome) {
            this.outcome = outcome;
        }
    }

    public static class CreateTrainingEventRequest {

        @NotBlank
        @Size(max = 255)
        private String title;

        @NotNull
        private LocalDate trainingDate;

        private Integer durationMinutes;

        @Size(max = 200)
        private String trainer;

        private String notes;

        private List<UUID> skillIds;

        @NotEmpty
        private List<AttendeeRequest> attendees;

        private Map<String, Object> customFields;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public LocalDate getTrainingDate() {
            return trainingDate;
        }

        public void setTrainingDate(LocalDate trainingDate) {
            this.trainingDate = trainingDate;
        }

        public Integer getDurationMinutes() {
            return durationMinutes;
        }

        public void setDurationMinutes(Integer durationMinutes) {
            this.durationMinutes = durationMinutes;
        }

        public String getTrainer() {
            return trainer;
        }

        public void setTrainer(String trainer) {
            this.trainer = trainer;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }

        public List<UUID> getSkillIds() {
            return skillIds;
        }

        public void setSkillIds(List<UUID> skillIds) {
            this.skillIds = skillIds;
        }

        public List<AttendeeRequest> getAttendees() {
            return attendees;
        }

        public void setAttendees(List<AttendeeRequest> attendees) {
            this.attendees = attendees;
        }

        public Map<String, Object> getCustomFields() {
            return customFields;
        }

        public void setCustomFields(Map<String, Object> customFields) {
            this.customFields = customFields;
        }
    }

    public static class PatchTrainingEventRequest {

        @Size(max = 255)
        private String title;

        private LocalDate trainingDate;

        private Integer durationMinutes;

        @Size(max = 200)
        private String trainer;

        private String notes;

        private List<UUID> skillIds;

        // When present, updates outcomes for the listed employees (attendance rows must exist).
        private List<AttendeeRequest> attendees;

        private Map<String, Object> customFields;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public LocalDate getTrainingDate() {
            return trainingDate;
        }

        public void setTrainingDate(LocalDate trainingDate) {
            this.trainingDate = trainingDate;
        }

        public Integer getDurationMinutes() {
            return durationMinutes;
        }

        public void setDurationMinutes(Integer durationMinutes) {
            this.durationMinutes = durationMinutes;
        }

        public String getTrainer() {
            return trainer;
        }

        public void setTrainer(String trainer) {
            this.trainer = trainer;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }

        public List<UUID> getSkillIds() {
            return skillIds;
        }

        public void setSkillIds(List<UUID> skillIds) {
            this.skillIds = skillIds;
        }

        public List<AttendeeRequest> getAttendees() {
            return attendees;
        }

        public void setAttendees(List<AttendeeRequest> attendees) {
            this.attendees = attendees;
        }

        public Map<String, Object> getCustomFields() {
            return customFields;
        }

        public void setCustomFields(Map<String, Object> customFields) {
            this.customFields = customFields;
        }
    }

    public static class AttendeeDto {

        private UUID id;
        private UUID employeeId;
        private String employeeNumber;
        private String outcome;

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public UUID getEmployeeId() {
            return employeeId;
        }

        public void setEmployeeId(UUID employeeId) {
            this.employeeId = employeeId;
        }

        public String getEmployeeNumber() {
            return employeeNumber;
        }

        public void setEmployeeNumber(String employeeNumber) {
            this.employeeNumber = employeeNumber;
        }

        public String getOutcome() {
            return outcome;
        }

        public void setOutcome(String outcome) {
            this.outcome = outcome;
        }
    }

    public static class TrainingEventDto {

        private UUID id;
        private String title;
        private LocalDate trainingDate;
        private Integer durationMinutes;
        private String trainer;
        private String notes;
        private List<UUID> skillIds;
        private List<AttendeeDto> attendees;
        private Map<String, Object> customFields;

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public LocalDate getTrainingDate() {
            return trainingDate;
        }

        public void setTrainingDate(LocalDate trainingDate) {
            this.trainingDate = trainingDate;
        }

        public Integer getDurationMinutes() {
            return durationMinutes;
        }

        public void setDurationMinutes(Integer durationMinutes) {
            this.durationMinutes = durationMinutes;
        }

        public String getTrainer() {
            return trainer;
        }

        public void setTrainer(String trainer) {
            this.trainer = trainer;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }

        public List<UUID> getSkillIds() {
            return skillIds;
        }

        public void setSkillIds(List<UUID> skillIds) {
            this.skillIds = skillIds;
        }

        public List<AttendeeDto> getAttendees() {
            return attendees;
        }

        public void setAttendees(List<AttendeeDto> attendees) {
            this.attendees = attendees;
        }

        public Map<String, Object> getCustomFields() {
            return customFields;
        }

        public void setCustomFields(Map<String, Object> customFields) {
            this.customFields = customFields;
        }
    }

    public static class TrainingHistoryEntryDto {

        private UUID trainingEventId;
        private String title;
        private LocalDate trainingDate;
        private Integer durationMinutes;
        private String trainer;
        private String outcome;
        private List<UUID> skillIds;

        public UUID getTrainingEventId() {
            return trainingEventId;
        }

        public void setTrainingEventId(UUID trainingEventId) {
            this.trainingEventId = trainingEventId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public LocalDate getTrainingDate() {
            return trainingDate;
        }

        public void setTrainingDate(LocalDate trainingDate) {
            this.trainingDate = trainingDate;
        }

        public Integer getDurationMinutes() {
            return durationMinutes;
        }

        public void setDurationMinutes(Integer durationMinutes) {
            this.durationMinutes = durationMinutes;
        }

        public String getTrainer() {
            return trainer;
        }

        public void setTrainer(String trainer) {
            this.trainer = trainer;
        }

        public String getOutcome() {
            return outcome;
        }

        public void setOutcome(String outcome) {
            this.outcome = outcome;
        }

        public List<UUID> getSkillIds() {
            return skillIds;
        }

        public void setSkillIds(List<UUID> skillIds) {
            this.skillIds = skillIds;
        }
    }
}
