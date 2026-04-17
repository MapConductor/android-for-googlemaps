# GoogleMapViewScope

## Signature

```kotlin
class GoogleMapViewScope : MapViewScope()
```

## Description

`GoogleMapViewScope` is a specialized scope class for the Google Maps implementation within the
MapConductor framework. It extends the base `MapViewScope`, inheriting all common map
functionalities, while also providing access to features unique to the Google Maps SDK.

This scope is the context for map configuration and control when using Google Maps as the provider.
Use it to access Google-specific functionalities that are not part of the common map abstraction
layer, such as Street View.

## Example

`GoogleMapViewScope` is provided as the receiver within the `content` lambda of `GoogleMapView`.
Overlay composables and Google-specific extensions are called within this scope.

```kotlin
GoogleMapView(
    state = mapState,
    modifier = Modifier.fillMaxSize(),
) {
    // 'this' is GoogleMapViewScope
    // Add overlays using composables from MapViewScope or Google-specific extensions here.
    Marker(state = MarkerState(id = "marker-1", position = GeoPoint(35.681236, 139.767125)))
}
```