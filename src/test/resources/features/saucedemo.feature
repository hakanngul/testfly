Feature: Sauce Demo login and shopping cart

  Background:
    Given the user is on the Sauce Demo login page

  Scenario: Standard user can log in and see products
    When the user logs in with username "standard_user" and password "secret_sauce"
    Then the products page is displayed

  Scenario: Locked out user sees an error message
    When the user logs in with username "locked_out_user" and password "secret_sauce"
    Then an error message containing "locked out" is displayed

  Scenario: User can add a product to the cart
    Given the user is logged in as "standard_user" with password "secret_sauce"
    When the user adds the first product to the cart
    Then the cart badge shows "1"
