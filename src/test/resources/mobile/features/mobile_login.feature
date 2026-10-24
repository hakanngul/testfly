# Run by MobileEngineBridgeTest against the fake mobilecli server; not part of any runner.
@mobile
Feature: Native app login through the Cucumber bridge

  @pass
  Scenario: Signing in shows the greeting
    When I sign in on the device as "linus"
    Then the device shows "Hello, linus"

  @fail
  Scenario: A missing element fails with a device screenshot
    Then the device shows "Not on this screen"
