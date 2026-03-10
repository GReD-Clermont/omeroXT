# OMEROXT

An [Imaris XTension](https://imaris.oxinst.com/open) to load images and ROIs from
an [OMERO](https://www.openmicroscopy.org/omero/) server directly into [Imaris](https://imaris.oxinst.com/).

## Description

omeroXT is a Java-based Imaris XTension that bridges [OMERO](https://www.openmicroscopy.org/omero/) and
[Imaris](https://imaris.oxinst.com/), enabling users to browse an OMERO server and load images and regions of interest
(ROIs) into a running Imaris instance through a graphical interface.

### Features

- **Connect to OMERO**: Log in to an OMERO server and browse projects, datasets, and images.
- **Load images**: Transfer image data (8-bit, 16-bit, and float) from OMERO to Imaris, including channel colors/names,
  spatial calibration, and acquisition metadata.
- **Load ROIs as Spots**: Convert OMERO point ROIs into Imaris Spots objects.
- **Load ROIs as Surfaces**: Convert OMERO shape ROIs (rectangles, ellipses, polygons, etc.) into Imaris Surfaces using
  label images.
- **Split Surfaces**: Load each ROI as a separate named Surfaces object inside an Imaris data container.

## Requirements

- **Java**: JDK 8 or higher
- **Imaris**: 10.0+ (with the ImarisLib Java library)
- **OMERO server**: A running OMERO instance (>= 5.6))
- **Maven**: 3.6+ (for building)

### Dependencies

| Dependency                                                                  | Version |
|-----------------------------------------------------------------------------|---------|
| [simple-omero-client](https://github.com/GReD-Clermont/simple-omero-client) | 5.19.0  |
| [imaris-lib](https://imaris.oxinst.com/open)                                | 10.0.0  |

## Building

```bash
mvn clean package
```

This produces a fat JAR with all dependencies:

```
target/omeroXT-1.0.0-SNAPSHOT-jar-with-dependencies.jar
```

## Usage

### Standalone

When starting the application from the JAR file, it will detect running Imaris instances and let
you select one from the GUI:

```bash
java -jar omeroXT-1.0.0-SNAPSHOT-jar-with-dependencies.jar
```

## Project Structure

```
fr.igred
├── imaris
│   ├── gui         — GUI components (main window, connection dialog)
│   ├── omero       — Service layer bridging OMERO and Imaris
│   └── xtension    — Main entry point (OMEROXTension)
└── omero
    ├── repository   — Image-to-Imaris conversion (Image2Imaris)
    └── roi          — ROI-to-Imaris conversion (ROI2Imaris)
```

## License

This project is licensed under the [GNU General Public License v2.0+](LICENSE).

## Authors

- **Pierre Pouchin** — [iGReD](https://www.gred-clermont.fr) (INSERM U1103 / CNRS UMR 6293 / UCA)

## Links

- **Repository**: <https://github.com/GReD-Clermont/omeroXT>
- **Issues**: <https://github.com/GReD-Clermont/omeroXT/issues>
- **Image.sc Forum**: <https://forum.image.sc/tag/omero>
