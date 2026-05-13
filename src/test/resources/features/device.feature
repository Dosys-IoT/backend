Feature: Device internal synchronization
  Scenario: Device fetches runtime config with valid device key
    Given an authenticated caregiver with a configured device and schedule
    When the device requests runtime configuration using a valid device key
    Then the backend returns the runtime configuration
    And the configuration includes the active schedule

  Scenario: Device reports an environmental reading visible to the caregiver
    Given an authenticated caregiver with a device
    When the device posts temperature and humidity using a valid internal key
    Then the backend stores the environmental reading
    And the caregiver can query the latest environmental reading

  Scenario: Device reports a confirmed intake event
    Given an authenticated caregiver with a configured device and schedule
    When the device posts an intake event with status TAKEN
    Then the backend stores the intake event
    And the adherence calendar includes the taken dose

  Scenario: Device reports stock update
    Given an authenticated caregiver with a configured device
    When the device posts a stock event for container 1
    Then the backend updates the remaining pills for container 1

  Scenario: Internal endpoint rejects wrong key
    Given an authenticated caregiver with a device
    When the device calls an internal endpoint with an invalid key
    Then the backend rejects the request with unauthorized or forbidden status
