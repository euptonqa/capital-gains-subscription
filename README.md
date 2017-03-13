# Capital Gains Subscription protected microservice

[![Apache-2.0 license](http://img.shields.io/badge/license-Apache-brightgreen.svg)](http://www.apache.org/licenses/LICENSE-2.0.html) [![Build Status](https://travis-ci.org/hmrc/capital-gains-subscription.svg)](https://travis-ci.org/hmrc/capital-gains-subscription) [![Download](https://api.bintray.com/packages/hmrc/releases/capital-gains-subscription/images/download.svg) ](https://bintray.com/hmrc/releases/capital-gains-subscription/_latestVersion)

## Summary

This protected microservice provides RESTful endpoints for the subscription of an individual to the Capital Gains Tax service. It communicates with ETMP via DES, to which DES responds with a CGT reference.
It also enrols an individual's GG account to the Capital Gains Tax service. 

There is a frontend microservice [Capital-Gains-Subscription-Frontend](https://github.com/hmrc/capital-gains-subscription-frontend) that provides the views and controllers which interact with this protected microservice.

### Run the application

To run the application execute

```
sbt 'run 9770'
```

### Test the application

To test the application execute

```
sbt test
```

## Requirements

This service is written in [Scala](http://www.scala-lang.org/) and [Play](http://playframework.com/), so needs a [JRE] to run.


## Dependencies

* Audit - datastream

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")

## End points

<table>
    <tr>
        <th>Path</th>
        <th>Supported Methods</th>
        <th>Description</th>
    </tr>
    <tr>
        <td>/capital-gains-subscription/subscribe/resident/individual</td>
        <td>POST</td>
        <td>End point that registers a business partner in ETMP and subscribes a resident individual user to the Capital Gains Tax Service. Returns a JSON object containing a subscription reference. This requires the following request parameters: nino : String</td>
    </tr>
    <tr>
        <td>/capital-gains-subscription/subscribe/non-resident/individual-nino</td>
        <td>POST</td>
        <td>End point that registers a business partner in ETMP and subscribes a non-resident individual user known to the HMRC to the Capital Gains Tax Service. Returns a JSON object containing a subscription reference. This requires the following request parameters: nino : String</td>
    </tr>
    <tr>
        <td>/capital-gains-subscription/subscribe/non-resident/individual</td>
        <td>POST</td>
        <td>End point that registers a business partner in ETMP and subscribes a non-resident individual user with no HMRC footprint to the Capital Gains Tax Service. Returns a JSON object containing a subscription reference. This requires a JSON request body containing:
              firstName: String,
              lastName: String,
              addressLineOne: String,
              addressLineTwo: Option[String],
              townOrCity: String,
              county: Option[String],
              postCode: String,
              country: String
        </td>
    </tr>
    <tr>
        <td>/capital-gains-subscription/subscribe/company</td>
        <td>POST</td>
        <td>End point that subscribes a non-resident company to the Capital Gains Tax Service. Returns a JSON object containing a subscription reference. This requires a JSON request body containing:
            sap: Option[String],
            contactDetailsModel: Option[ContactDetailsModel],
            contactAddress: Option[CompanyAddressModel],
            registeredAddress: Option[CompanyAddressModel]
        </td>
    </tr>
    <tr>
        <td>/capital-gains-subscription/subscribe/agent</td>
        <td>POST</td>
        <td>End point that enrols a non-resident agent for the Capital Gains Tax Service. Returns a JSON object containing a subscription reference. This requires a JSON request body containing:
            sap: String,
            arn: String
        </td>
    </tr>
</table>

## POST /capital-gains-subscription/subscribe/resident/individual

Registers a business partner in ETMP and subscribes a resident individual user to the Capital Gains Tax Service.

### Example of usage

POST /capital-gains-subscription/subscribe/resident/individual?nino=XX123456X

**Response**
```json
{
    "cgtRef": "CGT123456789098"
}
```

## POST /capital-gains-subscription/subscribe/non-resident/individual-nino

Registers a business partner in ETMP and subscribes a non-resident individual user known to the HMRC to the Capital Gains Tax Service.

### Example of usage

POST /capital-gains-subscription/subscribe/non-resident/individual-nino?nino=XX123456X

**Response**
```json
{
    "cgtRef": "CGT123456789098"
}
```

## POST /capital-gains-subscription/subscribe/non-resident/individual

Registers a business partner in ETMP and subscribes a non-resident individual user with no HMRC footprint to the Capital Gains Tax Service.

### Example of usage

POST /capital-gains-subscription/subscribe/non-resident/individual

**Request Body**
```json
{
    "firstName": "Joe",
    "lastName": "Bloggs",
    "addressLineOne": "60 Example Lane",
    "addressLineTwo": "Example Region",
    "townOrCity": "Example Town",
    "county": "Example County",
    "postCode": "X123 1XX",
    "country": "DE"
}
```

**Response**
```json
{
    "cgtRef": "CGT123456789098"
}
```

## POST /capital-gains-subscription/subscribe/company

Subscribes a non-resident company to the Capital Gains Tax Service.

### Example of usage

POST /capital-gains-subscription/subscribe/company

**Request Body**
```json
{
    "sap": "123456789098765",
    "contactDetailsModel": {
        "contactName": "Joe Bloggs",
        "telephone": "01111 111111",
        "email": "example@example.com"
    },
    "contactAddress": {
        "addressLine1": "60 Example Lane",
        "addressLine2": "Example Region",
        "addressLine3": "Example Town",
        "addressLine4": "Example County",
        "postCode": "X123 1XX",
        "country": "DE"
    },
    "registeredAddress": {
        "addressLine1": "60 Example Lane",
        "addressLine2": "Example Region",
        "addressLine3": "Example Town",
        "addressLine4": "Example County",
        "postCode": "X123 1XX",
        "country": "DE"
    }
}
```

**Response**
```json
{
    "cgtRef": "CGT123456789098"
}
```

## POST /capital-gains-subscription/subscribe/agent

Enrols a non-resident agent for the Capital Gains Tax Service.

### Example of usage

POST /capital-gains-subscription/subscribe/agent

**Request Body**
```json
{
    "sap": "123456789098765",
    "arn": "JARN1234567"
}
```

**Response**
```
    204 NO_CONTENT Response
```