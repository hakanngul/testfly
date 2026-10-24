# Native iOS login example. Run with IosLoginCucumberRunner (see its Javadoc).
# The @mobile tag opens the device from the 'mobile:' section of the active profile.
@mobile
Feature: Native iOS app login

  Scenario: Signing in with a member id
    Given the app is on the home screen
    When I open the login form
    And I sign in with the member id and password from the environment
    Then the login form is closed
