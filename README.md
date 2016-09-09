# Capital Gains Subscription protected microservice

[![Apache-2.0 license](http://img.shields.io/badge/license-Apache-brightgreen.svg)](http://www.apache.org/licenses/LICENSE-2.0.html) [![Build Status](https://travis-ci.org/hmrc/capital-gains-subscription.svg)](https://travis-ci.org/hmrc/capital-gains-subscription) [![Download](https://api.bintray.com/packages/hmrc/releases/capital-gains-subscription/images/download.svg) ](https://bintray.com/hmrc/releases/capital-gains-subscription/_latestVersion)

## Summary

This protected microservice provides RESTful endpoints for the subscription of an individual to the Capital Gains Tax service. It communicates with ETMP via DES, to which DES responds with a CGT reference.
It also enrols an individual's GG account to the Capital Gains Tax service. 

There is a frontend microservice [Capital-Gains-Subscription-Frontend](https://github.com/hmrc/capital-gains-subscription-frontend) that provides the views and controllers which interact with this protected microservice.

## Requirements

This service is written in [Scala](http://www.scala-lang.org/) and [Play](http://playframework.com/), so needs a [JRE] to run.


## Dependencies

* Audit - datastream

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")