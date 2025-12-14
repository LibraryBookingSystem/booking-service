package com.library.booking_service.dto;

import java.util.List;

/**
 * DTO for policy validation response (from Policy Service)
 */
public class PolicyValidationResponse {
    
    private boolean valid;
    private List<String> violations;
    
    public PolicyValidationResponse() {}
    
    public boolean isValid() {
        return valid;
    }
    
    public void setValid(boolean valid) {
        this.valid = valid;
    }
    
    public List<String> getViolations() {
        return violations;
    }
    
    public void setViolations(List<String> violations) {
        this.violations = violations;
    }
}





