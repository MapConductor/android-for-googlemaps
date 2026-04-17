# Google Maps Type Aliases

This document provides a reference for the type aliases used to map abstract `mapconductor` concepts
to the concrete classes provided by the Google Maps Android SDK. These aliases are part of an
abstraction layer, allowing for consistent type usage within the `mapconductor` ecosystem while
internally utilizing the Google Maps implementation.

### Description

The following `typealias` declarations create convenient, descriptive names for the underlying
Google Maps SDK objects. This helps in maintaining clean, readable, and provider-agnostic code in
the upper layers of the application.

### Type Aliases

The table below lists each type alias, the Google Maps SDK class it maps to, and a brief description
of its purpose.

- Description: An alias for a map marker object used to indicate a single location on the map.
- Description: An alias for a circle shape. It uses a `Polygon` implementation to support
  geodesic-correct circles, which the standard `Circle` object does not.
- Description: An alias for a polyline object, which represents a series of connected line segments
  on the map.
- Description: An alias for a polygon object, which represents an enclosed, fillable area on the
  map.
- Description: An alias for a ground overlay, which is an image that is fixed to a specific
  geographical location on the map.
- Description: An alias for a tile overlay, used for adding a custom set of raster images (tiles) on
  top of the base map.
