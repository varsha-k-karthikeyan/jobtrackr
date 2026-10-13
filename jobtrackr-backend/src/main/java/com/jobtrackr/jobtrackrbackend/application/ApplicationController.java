package com.jobtrackr.jobtrackrbackend.application;

import com.jobtrackr.jobtrackrbackend.application.dto.ApplicationDetailResponse;
import com.jobtrackr.jobtrackrbackend.application.dto.ApplicationResponse;
import com.jobtrackr.jobtrackrbackend.application.dto.CreateApplicationRequest;
import com.jobtrackr.jobtrackrbackend.application.dto.MoveApplicationRequest;
import com.jobtrackr.jobtrackrbackend.application.dto.UpdateApplicationRequest;
import com.jobtrackr.jobtrackrbackend.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

// Thin HTTP layer: bind and validate the request, delegate to the service, pick the status code
@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @GetMapping("/board")
    public Map<ApplicationStatus, List<ApplicationResponse>> board() {
        return applicationService.getBoard();
    }

    // e.g. /applications?status=APPLIED&searchText=google&sort=appliedOn,desc&page=0&size=10
    @GetMapping
    public PageResponse<ApplicationResponse> list(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) String searchText,
            @ParameterObject
            @PageableDefault(size = 10, sort = "appliedOn", direction = Sort.Direction.DESC) Pageable pageable) {
        return applicationService.list(status, searchText, pageable);
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody CreateApplicationRequest request) {
        ApplicationResponse created = applicationService.create(request);
        // 201 Created with a Location header pointing at the new resource
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public ApplicationDetailResponse get(@PathVariable Long id) {
        return applicationService.get(id);
    }

    @PutMapping("/{id}")
    public ApplicationResponse update(@PathVariable Long id, @Valid @RequestBody UpdateApplicationRequest request) {
        return applicationService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        applicationService.delete(id);
    }

    @PatchMapping("/{id}/move")
    public ApplicationResponse move(@PathVariable Long id, @Valid @RequestBody MoveApplicationRequest request) {
        return applicationService.move(id, request);
    }
}
