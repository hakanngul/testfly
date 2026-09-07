Feature: Agentic E-Commerce Automation with TestFly
  Demonstrates natural language goal execution and semantic AI assertions in Gherkin BDD.

  Background:
    Given the user is on the Sauce Demo login page

  @Agentic
  Scenario: Autonomous login and cart flow
    When the agent executes goal "Enter username 'standard_user' and password 'secret_sauce', then click Login"
    Then the page satisfies AI condition "The user is logged in and the products catalog is displayed"
    And the page violates AI condition "Error banner or locked out message"
    When the agent executes goal "Add the backpack to the cart and navigate to the cart"
    Then the page satisfies AI condition "Shopping cart contains Sauce Labs Backpack"
