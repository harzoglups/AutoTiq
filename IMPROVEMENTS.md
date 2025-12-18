# AutoTiq - Improvement Suggestions

This document lists potential improvements and new features to consider before moving to closed beta testing.

## 🎯 Top 5 Priorities for Closed Beta

1. **Test Mode** - Allow beta testers to simulate zone entry without physically moving
2. **Activation History** - Log when Fairtiq was automatically launched (helps with bug reports)
3. **Empty State Message** - Show helpful message when no points exist ("Long press on map to add your first point")
4. **Fairtiq Installation Check** - Verify Fairtiq is installed and show warning if not
5. **Global Pause Mode** - Quick toggle to temporarily disable all points

---

## 🎨 Ergonomics / UX

### 1. Visual Feedback for Long Press
**Status**: ✅ Implemented (v1.0.7)  
**Description**: Visual and haptic feedback when long press is detected.  
**Implementation**: 
- Ripple animation with expanding circle at touch point
- Short vibration feedback at beginning of long press
- Confirmation dialog now required before creating marker

**Priority**: Medium  
**Effort**: Low

---

### 2. Better Proximity Zone Visibility
**Status**: Basic circle displayed  
**Description**: Users may not notice the red proximity circle around markers.  
**Suggestion**:
- Add subtle pulse animation to the circle
- Make circle more prominent with better colors/opacity
- Option to toggle circle visibility in settings

**Priority**: Low  
**Effort**: Low

---

### 3. Empty State Message
**Status**: ✅ Implemented (v1.0.7)  
**Description**: Helpful message displayed when no zones exist.  
**Implementation**:
- Card shown at bottom of map: "No zones yet"
- Instructions: "Long press on the map to add your first zone, or use the search"
- Orange title for visibility
- Automatically hidden when zones exist or search result is shown
- Implemented in all 9 supported languages

**Priority**: High  
**Effort**: Low

---

### 4. Search Result Preview
**Status**: ✅ Implemented (v1.0.7)  
**Description**: Search results show enhanced preview with info card.  
**Implementation**:
- Large red pin marker for search results (more visible than zone markers)
- Info card automatically shown with location name
- "Create zone here" button in info card
- Red marker persists when closing info card (allows exploration)
- Red marker removed when creating zone or clicking on map
- Implemented in all 9 supported languages

**Priority**: Low  
**Effort**: Medium

---

## 🚀 Features

### 5. Export/Import Zones
**Status**: ✅ Implemented (v1.0.8)  
**Description**: Backup and restore zones for device migration or backup.  
**Implementation**:
- "Backup & Restore" section in Settings screen
- Export all zones to JSON file via Storage Access Framework
- Import zones from JSON file (always merges with existing zones)
- JSON format: `{version: 1, exportDate: ISO-8601, zones: [{name, latitude, longitude, startHour, startMinute, endHour, endMinute}]}`
- Full validation of imported data (coordinates range, time values)
- Success/error messages in all 9 supported languages
- **Note**: No cloud sync or user-to-user sharing (local backup only)

**Priority**: Medium  
**Effort**: Medium

**Known Limitations**:
- Always merges imported zones with existing ones (no "replace all" option yet)
- No dialog to choose between merge/replace strategies
- Can be added later if needed

---

### 6. Point Groups/Categories
**Status**: Not implemented  
**Description**: All points are treated equally, no way to organize them.  
**Suggestion**:
- Create categories: "Work", "Home", "Vacation", etc.
- Different marker colors per category
- Enable/disable entire categories at once
- Filter map view by category

**Priority**: Low  
**Effort**: High

**Implementation Notes**:
- Add `category` field to MapPoint entity
- Add CategoryRepository and Category entity
- Update UI to show category selector
- Add category filter button on map

---

### 7. Activation History
**Status**: Not implemented  
**Description**: No way to see when/where Fairtiq was automatically launched.  
**Suggestion**:
- Log each automatic launch with timestamp and location
- Show history list in Settings
- Include point name, date/time, distance from point
- Add "Test Mode" button to simulate activation

**Priority**: High  
**Effort**: Medium

**Implementation Notes**:
- Create ActivationLog entity in database
- Log in LocationCheckWorker when Fairtiq is launched
- Add ActivationHistoryScreen accessible from Settings
- Limit history to last 30 days or 100 entries

---

### 8. Test Mode
**Status**: Not implemented  
**Description**: Can't test the system without physically moving to a point.  
**Suggestion**:
- Add "Simulate Proximity" button in Settings
- Select a point and trigger its activation manually
- Shows notification, vibrates, launches Fairtiq
- Essential for beta testers to verify functionality

**Priority**: High  
**Effort**: Low

**Implementation Notes**:
- Add button in Settings: "Test Mode"
- Show dialog with list of points to test
- Call same launch logic as LocationCheckWorker
- Add toast message: "Test activation for [Point Name]"

---

## ⚙️ Advanced Settings

### 9. Launch Delay
**Status**: Instant launch  
**Description**: Fairtiq launches immediately when entering zone.  
**Suggestion**:
- Add optional delay (5-10 seconds) before launch
- Show cancellable notification during delay
- Prevents false positives from GPS drift

**Priority**: Low  
**Effort**: Medium

**Implementation Notes**:
- Add `launchDelaySeconds` to AppSettings
- Show notification with countdown and cancel button
- Use Handler.postDelayed() in LocationCheckWorker

---

### 10. Customizable Notifications
**Status**: Fixed notification text  
**Description**: Notification text is hardcoded.  
**Suggestion**:
- Option to disable notification entirely
- Customize notification text
- Choose notification sound
- Adjust notification priority

**Priority**: Low  
**Effort**: Low

---

### 11. Battery Saving Mode
**Status**: No battery optimization  
**Description**: App checks location at fixed interval regardless of context.  
**Suggestion**:
- Show estimated battery consumption in Settings
- "Battery Saver" mode with longer check intervals
- Smart intervals based on time of day
- Disable during nights/weekends if not needed

**Priority**: Medium  
**Effort**: Medium

**Implementation Notes**:
- Use Android's Battery Manager to estimate consumption
- Add preset profiles: "Aggressive", "Balanced", "Battery Saver"
- Integrate with active days feature (already exists)

---

## 🔒 Security / Reliability

### 12. Fairtiq Installation Check
**Status**: Not implemented  
**Description**: No validation that Fairtiq is installed.  
**Suggestion**:
- Check if Fairtiq is installed on app startup
- Show warning banner in Settings if not found
- Provide link to Play Store to install Fairtiq
- Test in Settings to verify package can be launched

**Priority**: High  
**Effort**: Low

**Implementation Notes**:
```kotlin
fun isFairtiqInstalled(context: Context): Boolean {
    return try {
        context.packageManager.getPackageInfo("com.fairtiq.android", 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
```

---

### 13. Launch Error Handling
**Status**: Basic error handling  
**Description**: No feedback if Fairtiq fails to launch.  
**Suggestion**:
- Catch exceptions when launching Fairtiq
- Log errors to activation history
- Show notification to user if launch fails
- Provide troubleshooting tips in error message

**Priority**: Medium  
**Effort**: Low

---

### 14. Global Pause Mode
**Status**: Not implemented  
**Description**: Can't quickly disable all points temporarily.  
**Suggestion**:
- Add "Pause All" toggle in main screen
- Keeps background tracking running but skips launch logic
- Useful for vacations, weekends, etc.
- Resume with single tap

**Priority**: High  
**Effort**: Low

**Implementation Notes**:
- Add `globalPauseEnabled` to AppSettings
- Add prominent toggle button on map screen
- Check flag in LocationCheckWorker before launching Fairtiq
- Show visual indicator on map when paused (e.g., grayed out markers)

---

## 🐛 Polish / Details

### 15. Marker Animation
**Status**: Markers appear instantly  
**Description**: New markers just pop into existence.  
**Suggestion**:
- Fade in + scale animation when adding new marker
- Bounce animation when tapping marker
- Smooth transitions when updating markers

**Priority**: Low  
**Effort**: Low

---

### 16. Undo/Redo Actions
**Status**: Not implemented  
**Description**: No way to undo accidental deletions.  
**Suggestion**:
- Show Snackbar after deletion: "Point deleted" with "Undo" button
- Keep deleted point in memory for 5 seconds
- Restore if user taps "Undo"

**Priority**: Medium  
**Effort**: Medium

**Implementation Notes**:
- Use Snackbar with action button
- Keep reference to deleted MapPoint
- Re-insert into database if undo is clicked

---

### 17. Smart Zoom
**Status**: Fixed zoom when navigating  
**Description**: Zoom is fixed at 16.0 when navigating to markers.  
**Suggestion**:
- Calculate optimal zoom based on nearby markers
- Show all relevant markers in viewport
- Different zoom for single marker vs. cluster

**Priority**: Low  
**Effort**: Medium

---

### 18. Contextual Help
**Status**: Basic "How it works" text  
**Description**: Some settings are not self-explanatory.  
**Suggestion**:
- Add "?" button next to each setting
- Show tooltip or dialog explaining the setting
- Add examples and recommended values
- Link to online documentation

**Priority**: Low  
**Effort**: Low

---

## 📊 Analytics & Monitoring

### 19. Usage Statistics
**Status**: Not implemented  
**Description**: No insight into app usage.  
**Suggestion**:
- Track number of points created
- Count automatic launches per week
- Monitor background task success rate
- Show stats in Settings

**Priority**: Low  
**Effort**: Medium

---

### 20. Debug Mode
**Status**: Logs to logcat only  
**Description**: Beta testers can't easily see debug info.  
**Suggestion**:
- Add "Developer Mode" in Settings
- Show recent logs in-app
- Export logs to file for bug reports
- Display current GPS accuracy and check status

**Priority**: Medium  
**Effort**: Medium

---

## 🌍 Localization

### 21. Additional Languages
**Status**: ✅ Implemented (v1.0.7)  
**Description**: Extended language support for broader user base.  
**Implementation**:
- 9 languages now supported: EN, FR, DE, CS, ES, IT, NB, PT, SV
- All UI strings translated including recent features
- Consistent "zone" terminology across all languages
- Italian, Spanish, Portuguese added (Fairtiq coverage)
- Czech, Norwegian, Swedish added (Nordic coverage)

**Priority**: Low  
**Effort**: Low

---

## 🎨 Customization

### 22. Theme Options
**Status**: Single Material 3 theme  
**Description**: No theme customization.  
**Suggestion**:
- Dark mode toggle (currently follows system)
- Accent color picker
- Map style options (already have Street/Topo)
- Custom marker colors

**Priority**: Low  
**Effort**: Medium

---

## 📝 Documentation

### 23. In-App Tutorial
**Status**: Basic onboarding  
**Description**: Onboarding only covers permissions.  
**Suggestion**:
- Interactive tutorial showing key features
- Highlight map gestures (long press, tap, etc.)
- Explain proximity zones visually
- Show best practices for point placement

**Priority**: Low  
**Effort**: High

---

## Implementation Priority Matrix

| Feature | Priority | Effort | Impact | Recommended |
|---------|----------|--------|--------|-------------|
| Test Mode | High | Low | High | ✅ Yes |
| Activation History | High | Medium | High | ✅ Yes |
| Empty State Message | High | Low | Medium | ✅ Yes |
| Fairtiq Check | High | Low | High | ✅ Yes |
| Global Pause | High | Low | High | ✅ Yes |
| Undo Actions | Medium | Medium | Medium | ⚠️ Consider |
| Export/Import | Medium | Medium | Medium | ⚠️ Consider |
| Launch Error Handling | Medium | Low | Medium | ⚠️ Consider |
| Battery Saver | Medium | Medium | Low | ⚠️ Consider |
| Debug Mode | Medium | Medium | Medium | ⚠️ Consider |

---

## Notes

- This document is living and should be updated as features are implemented
- Priority levels: High (pre-beta), Medium (post-beta), Low (future)
- Effort estimates: Low (<4h), Medium (4-12h), High (>12h)
- Consider user feedback from closed beta before implementing Medium/Low priority items
