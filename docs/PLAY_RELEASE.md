# Google Play Release

## Distribution Boundary

Only MiPush Manager (`io.github.magisk317.mipush`) is distributed through Google Play. The XMSF runtime (`com.xiaomi.xmsf`) remains outside Google Play and continues to be published as an APK with the project release.

- GitHub APK: `:mipush:assembleGithubRelease`
- Play App Bundle: `:mipush:bundlePlayRelease`
- XMSF APK: `:app:assembleRelease`

The Play variant includes Google Play Billing. The GitHub variant registers a no-op billing provider and does not package the Billing dependency.

## Required External Configuration

1. Create the Play Console application for `io.github.magisk317.mipush` and enable Play App Signing.
2. Upload the first signed Play AAB manually if the application has not yet been initialized for Developer API uploads.
3. Create and activate these one-time in-app products:
   - `donate_099`
   - `donate_200`
   - `donate_999`
   - `donate_1999`
4. Configure a payments profile, license-test accounts, and an internal testing track.
5. Grant a Google Play Developer API service account permission to publish releases for this application.
6. Add the service-account JSON as `ANDROID_PUBLISHER_CREDENTIALS`:
   - GitHub Actions: repository secret containing raw JSON.
   - GitLab CI: protected and masked variable containing raw JSON or base64-encoded JSON.
7. Complete the store listing, privacy-policy URL, Data safety form, target-audience declaration, content rating, app-access declaration, and the Play Console declaration for broad package visibility where required.

Use `RELEASE_OWNER=github` or `RELEASE_OWNER=gitlab` consistently so only one pipeline publishes a tag.

## Client Permission Checklist

Play builds must merge `com.android.vending.BILLING` (declared in `mipush/src/play/AndroidManifest.xml` and `:magisk-ui-kit:billing`). Without this permission, BillingClient setup fails even if in-app products already exist in Play Console.

## Play Console Product Checklist

Use package `io.github.magisk317.mipush` and create active one-time products with these exact IDs (must match `ProductConfig.DONATION_IDS`):

| Product ID | Suggested price |
|---|---|
| `donate_099` | $0.99 |
| `donate_200` | $2.00 |
| `donate_999` | $9.99 |
| `donate_1999` | $19.99 |

Product type: managed / one-time, consumable. Status must be **Active** before the Play app can query details.

## Backend Requirement

The current donation products are consumable one-time purchases with no entitlement. Purchases are completed and consumed through Google Play Billing on the device, so no project-operated purchase backend is required for Play donations.

A backend becomes necessary only if a future release adds subscriptions, durable paid entitlements, cross-device purchase restoration outside Google Play's normal purchase query, server-controlled benefits, fraud-sensitive verification, or Real-time Developer Notifications.

Alipay donations use the separate 46-code-pay platform (`/Epay/mapi`) and do not depend on Play Console products.
