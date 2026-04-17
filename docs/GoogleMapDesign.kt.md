# GoogleMapDesign

The `GoogleMapDesign` sealed class represents the various base map tile types available in the
Google Maps SDK. It provides a type-safe way to manage and use Google's predefined map type
constants.

This class implements the `GoogleMapDesignType` interface, which is a type alias for
`MapDesignTypeInterface<Int>`.

```kotlin
sealed class GoogleMapDesign(
    override val id: Int,
) : GoogleMapDesignType
```

## Map Design Objects

The `GoogleMapDesign` class contains singleton objects that correspond to the map types defined in
`com.google.android.gms.maps.GoogleMap`.

- Description: Typical road map with streets, labels, and some points of interest.
- Description: Satellite imagery of the Earth without map labels.
- Description: A combination of satellite imagery and the normal map layer (roads, labels).
- Description: Topographic map showing elevation and land contours.
- Description: No base map tiles. Useful for displaying custom tile overlays exclusively.

---

## Functions

### getValue()

Retrieves the underlying integer constant for the map type, as defined in the `GoogleMap` class.

#### Signature

```kotlin
fun getValue(): Int
```

#### Description

This method returns the integer ID associated with a `GoogleMapDesign` instance. This ID corresponds
directly to one of the `MAP_TYPE_*` constants from the Google Maps SDK.

#### Returns

- Type: ``Int``
- Description: The integer constant for the map design type.

#### Example

```kotlin
import com.google.android.gms.maps.GoogleMap

// Get the integer value for the Satellite map type
val satelliteTypeId = GoogleMapDesign.Satellite.getValue()

// satelliteTypeId is now equal to GoogleMap.MAP_TYPE_SATELLITE
println(satelliteTypeId == GoogleMap.MAP_TYPE_SATELLITE) // true
```

---

## Companion Object Functions

The companion object provides factory methods for creating `GoogleMapDesign` instances from integer
IDs.

### Create()

Creates a `GoogleMapDesign` instance from a given integer map type ID.

#### Signature

```kotlin
fun Create(id: Int): GoogleMapDesign
```

#### Description

This factory method looks up the appropriate `GoogleMapDesign` object based on the provided integer
`id`. If the `id` does not match any of the known map types, it defaults to `GoogleMapDesign.None`.

#### Parameters

- ``id``
    - Type: ``Int``
    - Description: The integer ID of the map type (e.g., `GoogleMap.MAP_TYPE_NORMAL`).

#### Returns

- Type: ``GoogleMapDesign``
- Description: The corresponding `GoogleMapDesign` object, or `GoogleMapDesign.None` if the ID is
  not recognized.

#### Example

```kotlin
import com.google.android.gms.maps.GoogleMap

// Create a GoogleMapDesign instance from a constant
val mapDesign = GoogleMapDesign.Create(GoogleMap.MAP_TYPE_TERRAIN)

// mapDesign is now GoogleMapDesign.Terrain
println(mapDesign is GoogleMapDesign.Terrain) // true

// Create from an unknown ID
val unknownDesign = GoogleMapDesign.Create(99)

// unknownDesign is now GoogleMapDesign.None
println(unknownDesign is GoogleMapDesign.None) // true
```

### toMapDesignType()

Creates an instance that conforms to the `GoogleMapDesignType` interface from a given integer map
type ID.

#### Signature

```kotlin
fun toMapDesignType(id: Int): GoogleMapDesignType
```

#### Description

This function is similar to `Create()`, but it returns the result as the `GoogleMapDesignType`
interface. This is useful for maintaining abstraction in your code. It defaults to
`GoogleMapDesign.None` for unrecognized IDs.

#### Parameters

- ``id``
    - Type: ``Int``
    - Description: The integer ID of the map type (e.g., `GoogleMap.MAP_TYPE_HYBRID`).

#### Returns

- Type: ``GoogleMapDesignType``
- Description: An object implementing the `GoogleMapDesignType` interface, or `GoogleMapDesign.None`
  if the ID is not recognized.

#### Example

```kotlin
import com.google.android.gms.maps.GoogleMap

// Get the map design type for Hybrid
val designType: GoogleMapDesignType = GoogleMapDesign.toMapDesignType(GoogleMap.MAP_TYPE_HYBRID)

// Use the returned value
val mapId = designType.getValue() // Returns GoogleMap.MAP_TYPE_HYBRID
```

---

# GoogleMapDesignType

A type alias for `MapDesignTypeInterface<Int>`, representing a generic interface for a map design
type where the underlying value is an `Int`.

#### Signature

```kotlin
typealias GoogleMapDesignType = MapDesignTypeInterface<Int>
```

#### Description

This type alias provides a clear and specific name for the map design interface used within the
context of Google Maps, enhancing code readability. All `GoogleMapDesign` objects conform to this
interface.
