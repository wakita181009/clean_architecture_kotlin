Create a new domain entity with all required components.

Entity name: $ARGUMENTS

Follow the hexagonal architecture pattern to create:

## Domain Layer (`domain/`)
1. Entity class in `domain/entity/{category}/`
2. Value objects in `domain/valueobject/{category}/` (Id, Key, etc.)
   - Use `private constructor` + `companion object { operator fun invoke() }`
3. Repository interface in `domain/repository/{category}/`
4. Port interface in `domain/port/{category}/` (if external API needed)
5. Error types in `domain/error/`

## Infrastructure Layer (`infrastructure/`)
1. Repository implementation in `infrastructure/repository/{category}/`
2. API adapter in `infrastructure/adapter/{category}/` (if port defined)
3. DTOs with `toDomain()` methods
4. Database migration in `infrastructure/src/main/resources/db/migration/`

## Application Layer (`application/`)
1. UseCase in `application/usecase/{category}/`

## Framework Layer (`framework/`)
1. Runner in `framework/runner/` (if background job needed)

Read `.claude/docs/architecture.md` for detailed patterns before implementing.
