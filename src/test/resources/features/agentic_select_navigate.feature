Feature: Agentic SELECT and NAVIGATE Primitives
  Demonstrates the SELECT and NAVIGATE agent action primitives in Gherkin BDD.
  NAVIGATE accepts an absolute URL, or a path resolved against execution.baseUrl.
  SELECT picks a <select> option by its visible text.

  Background:
    Given the user is on the Sauce Demo login page

  @Agentic @Navigate
  Scenario: Navigate to an absolute URL outside the configured baseUrl
    When the agent executes goal "Navigate to https://the-internet.herokuapp.com/dropdown"
    Then the page satisfies AI condition "A page headed 'Dropdown List' with a select box is displayed"

  @Agentic @Select
  Scenario: Select a dropdown option by its visible text
    When the agent executes goal "Navigate to https://the-internet.herokuapp.com/dropdown"
    And the agent executes goal "Select 'Option 2' from the dropdown"
    Then the page satisfies AI condition "The dropdown shows Option 2 as its selected value"
    And the page violates AI condition "The dropdown still shows the 'Please select an option' placeholder"

  @Agentic @Navigate
  Scenario: Navigate with a relative path resolved against execution.baseUrl
    When the agent executes goal "Enter username 'standard_user' and password 'secret_sauce', then click Login"
    And the agent executes goal "Navigate to /cart.html"
    Then the page satisfies AI condition "The Your Cart page is displayed with a Checkout button"
