# AutoTiq - Agent Guidelines

## Git Commit Policy
**ABSOLUTE RULE - NO EXCEPTIONS**: NEVER EVER create git commits unless the user uses one of these EXACT phrases:
- "commit" / "commits" / "tu peux commit" / "fais un commit"
- "make a commit" / "create a commit"
- "commit this" / "commit that"

**FORBIDDEN INTERPRETATIONS:**
- "corrige les workflow" = FIX ONLY, DO NOT COMMIT
- "tu peux y aller" = DO THE WORK, DO NOT COMMIT
- "parfait" / "super" / "ok" = ACKNOWLEDGMENT, NOT COMMIT AUTHORIZATION
- "continue" = CONTINUE WORKING, DO NOT COMMIT

**MANDATORY WORKFLOW:**
1. Make the requested changes (code, docs, configs, etc.)
2. Build and test if applicable
3. Show git status and diff
4. **STOP and WAIT** for explicit commit authorization
5. Only run `git commit` if user explicitly says "commit"

**If you commit without explicit authorization:**
- You MUST immediately offer to `git reset HEAD~1` to undo the commit
- You MUST apologize and acknowledge the violation of this rule

## Git Push Policy
**ABSOLUTE RULE - NO EXCEPTIONS**: NEVER EVER push commits to remote repositories.
- **NEVER run `git push`** under any circumstances
- **DO NOT offer or suggest** to push commits
- **DO NOT ask** if the user wants to push
- The user will push manually when they decide to
- This applies to all git push operations: `git push`, `git push origin main`, `git push --tags`, etc.

## Commit Message Format
**REQUIRED**: All commit messages MUST follow Conventional Commits specification.
- Format: `<type>(<scope>): <description>`
- Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`
- Example: `feat(map): add floating layer selection button`
- Example: `fix(markers): improve touch detection for marker deletion`
- Example: `docs(readme): update map interaction instructions`
- Keep description concise, use imperative mood ("add" not "added")

## Language Policy
**IMPORTANT**: All code, documentation, and commit messages MUST be in English, even if the conversation is in French.
- **Code**: Function names, variable names, class names, comments - all in English
- **Documentation**: All .md files must be in English (README.md, DEVELOPMENT.md, TODO.md, etc.)
- **Commit messages**: Always in English
- **Conversation**: Can be in French, but all written artifacts must be in English

## Build Commands
- **Java setup**: If gradle fails, set `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
- Build: `./gradlew build`
- Run tests: `./gradlew test`
- Run single test: `./gradlew test --tests "com.autotiq.domain.usecase.GetMapPointsUseCaseTest"`
- Run instrumented tests: `./gradlew connectedAndroidTest`
- Lint: `./gradlew lint`
- Assemble debug: `./gradlew assembleDebug`
- Install on device: `~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk`
- Launch app: `~/Library/Android/sdk/platform-tools/adb shell am start -n com.autotiq/.MainActivity`

## Testing Changes
**CRITICAL**: After making code changes, ALWAYS run these commands to build, install, and launch the app:
```bash
./gradlew assembleDebug && ~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk && ~/Library/Android/sdk/platform-tools/adb shell am start -n com.autotiq/.MainActivity
```
This ensures the user can immediately test the changes on their device.

## Architecture
Clean Architecture with 3 layers: **domain** (pure Kotlin), **data** (Android), **app** (UI)
- **Domain**: Business logic for map points, proximity detection, settings (no Android dependencies)
- **Data**: Repository implementations, Room database (points, proximity states), DataStore (settings)
- **App**: ViewModels (Hilt), Compose UI, OpenStreetMap integration, WorkManager background service

## Code Style
- **Language**: Kotlin with coroutines/Flow for async operations
- **Imports**: Group by (android, androidx, third-party, domain, data, ui), alphabetically sorted
- **Formatting**: 4 spaces indentation, 120 char line limit, trailing commas for multi-line
- **Types**: Explicit return types for public functions, type inference for local variables
- **Naming**: CamelCase for classes, camelCase for functions/variables, UPPER_SNAKE_CASE for constants
- **Nullability**: Prefer non-null types, use `?` explicitly when needed
- **Data classes**: Use for models/DTOs, implement mapping functions in separate mapper files
- **Sealed interfaces**: For UI states and navigation, prefer over sealed classes

## Error Handling
- Use `Result<T>` sealed class (Success/Error/Loading) for domain layer operations
- Catch exceptions in repository layer, never let them bubble to UI
- Display user-friendly error messages in UI, log technical details

## Dependency Injection
- Hilt for DI, annotate Application class with `@HiltAndroidApp`, Activity with `@AndroidEntryPoint`
- ViewModels with `@HiltViewModel`, inject use cases via constructor
- Provide dependencies in modules under `di/` package: DatabaseModule, AppModule
- Use `@HiltWorker` for WorkManager workers

## Location & Maps
- OpenStreetMap via osmdroid for map display
- Google Play Services Location for GPS access
- WorkManager for periodic background location checks
- Permissions: FINE_LOCATION, COARSE_LOCATION, BACKGROUND_LOCATION, VIBRATE

## Background Service
- LocationCheckWorker runs periodically based on user settings (seconds, not minutes)
- For intervals < 900s: Uses OneTimeWorkRequest with auto-rescheduling (WorkManager PeriodicWork min is 15min)
- Checks proximity to all saved points using Haversine formula (pure Kotlin, no Android deps in domain)
- Launches Fairtiq app (`com.fairtiq.android`) and vibrates when entering a zone
- Tracks proximity state per point in Room to avoid repeated triggers (outside → inside only)

## Testing
- Unit tests in `domain/` use JUnit + MockK, coroutines test with `kotlinx-coroutines-test` and `runTest`
- Repository tests mock DAO, verify mapping and error handling
- ViewModel tests use Turbine for Flow testing, verify state transitions
- Test naming: Use backticks for descriptive names (e.g., `` `invoke creates point with correct coordinates` ``)
- Structure: Given/When/Then comments for clarity

## Debugging
- View Worker logs: `~/Library/Android/sdk/platform-tools/adb logcat | grep LocationCheckWorker`
- Check devices: `~/Library/Android/sdk/platform-tools/adb devices`
- Launch app: `~/Library/Android/sdk/platform-tools/adb shell am start -n com.autotiq/.MainActivity`
