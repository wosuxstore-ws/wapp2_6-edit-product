package com.wl.orders.models;

// One row of get_people.php's "people" array (table wpeople + the role's name).
public class Person {
    public int id;
    public String name;
    public String number;
    public String image;  // file name only ("" = no photo); PeopleAdapter adds PEOPLE_IMAGE_BASE_URL
    public int roleId;
    public String role;   // wrole of that role_id, "" if the role was removed
}
