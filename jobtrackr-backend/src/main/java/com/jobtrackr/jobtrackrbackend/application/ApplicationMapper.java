package com.jobtrackr.jobtrackrbackend.application;

import com.jobtrackr.jobtrackrbackend.application.dto.ApplicationResponse;
import com.jobtrackr.jobtrackrbackend.application.dto.CreateApplicationRequest;
import com.jobtrackr.jobtrackrbackend.application.dto.UpdateApplicationRequest;
import com.jobtrackr.jobtrackrbackend.user.User;
import org.springframework.stereotype.Component;

// Hand-written entity <-> DTO conversion for applications. This is the only class that copies fields
// between the two, so a new column means: migration, entity, DTOs, and this mapper.
@Component
public class ApplicationMapper {

    public ApplicationResponse toResponse(Application application) {
        return new ApplicationResponse(
                application.getId(),
                application.getCompany(),
                application.getRole(),
                application.getStatus(),
                application.getPosition(),
                application.getJdUrl(),
                application.getJdText(),
                application.getLocation(),
                application.getSalaryMinLpa(),
                application.getSalaryMaxLpa(),
                application.getSource(),
                application.getAppliedOn(),
                application.getFollowUpOn(),
                application.getInterviewAt(),
                application.getVersion(),
                application.getCreatedAt(),
                application.getUpdatedAt());
    }

    // Owner and position are decided by the service, never taken from the request body
    public Application toEntity(CreateApplicationRequest request, User owner, int position) {
        Application application = new Application();
        application.setUser(owner);
        application.setStatus(request.status());
        application.setPosition(position);
        application.setCompany(request.company());
        application.setRole(request.role());
        application.setJdUrl(request.jdUrl());
        application.setJdText(request.jdText());
        application.setLocation(request.location());
        application.setSalaryMinLpa(request.salaryMinLpa());
        application.setSalaryMaxLpa(request.salaryMaxLpa());
        application.setSource(request.source());
        application.setAppliedOn(request.appliedOn());
        application.setFollowUpOn(request.followUpOn());
        application.setInterviewAt(request.interviewAt());
        return application;
    }

    // Copies the plain fields only. Status and position need column bookkeeping, so ApplicationService
    // handles them; id, owner and version are never copied from a request.
    public void applyUpdate(Application application, UpdateApplicationRequest request) {
        application.setCompany(request.company());
        application.setRole(request.role());
        application.setJdUrl(request.jdUrl());
        application.setJdText(request.jdText());
        application.setLocation(request.location());
        application.setSalaryMinLpa(request.salaryMinLpa());
        application.setSalaryMaxLpa(request.salaryMaxLpa());
        application.setSource(request.source());
        application.setAppliedOn(request.appliedOn());
        application.setFollowUpOn(request.followUpOn());
        application.setInterviewAt(request.interviewAt());
    }
}
