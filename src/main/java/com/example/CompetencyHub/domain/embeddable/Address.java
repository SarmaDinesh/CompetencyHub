package com.example.CompetencyHub.domain.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class Address {
    @Column(length = 200)
    private String street;

    @Column(length = 100)
    private String city;

    @Column(length = 50)
    private String state;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    protected Address() {
    }

    public Address(String street, String city, String state, String postalCode) {
        this.street = street;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
    }

    public String getStreet() { return street; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPostalCode() { return postalCode; }
}