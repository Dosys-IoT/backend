Feature: Access management
  Scenario: Caregiver registers and logs in successfully
    Given a new caregiver with valid account data
    When the caregiver registers an account
    And the caregiver logs in with the same credentials
    Then the backend returns a Bearer access token
    And the authenticated profile can be retrieved

  Scenario: Login rejects invalid credentials
    Given a registered caregiver
    When the caregiver logs in with an invalid password
    Then the backend rejects the request with unauthorized status
