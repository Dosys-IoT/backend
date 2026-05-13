Feature: Medication management
  Scenario: Caregiver links a pillbox and configures a medication schedule
    Given an authenticated caregiver
    When the caregiver creates a device
    And the caregiver configures container 1 with a medication
    And the caregiver creates a schedule for container 1
    Then the backend stores the schedule
    And the device runtime configuration contains the scheduled dose

  Scenario: Caregiver cannot access another caregiver device
    Given two authenticated caregivers
    And the first caregiver owns a device
    When the second caregiver tries to access that device medication data
    Then the backend rejects or hides the unauthorized data

  Scenario: Backend rejects invalid medication configuration
    Given an authenticated caregiver with a device
    When the caregiver tries to set negative remaining pills
    Then the backend rejects the request with bad request status
