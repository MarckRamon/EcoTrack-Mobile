# GrabTrash Mobile App Implementation Manifest

This document provides an overview of all the screens and components implemented in the GrabTrash mobile application for the trash pickup service.

## Screens

### 1. Order Pickup Screen (`OrderPickupActivity.kt`)

**Description:** This screen allows users to enter their personal details and select a location for trash pickup.

**File Location:** `app/src/main/java/com/example/ecotrack/ui/pickup/OrderPickupActivity.kt`

**Layout:** `app/src/main/res/layout/activity_order_pickup.xml`

**Features:**
- Personal details form (name, email)
- Map integration for location selection
- Location address display and edit option
- Proceed to payment button

### 2. Map Picker Screen (`MapPickerActivity.kt`)

**Description:** This screen provides a full-screen map for users to select their precise pickup location.

**File Location:** `app/src/main/java/com/example/ecotrack/ui/pickup/MapPickerActivity.kt`

**Layout:** `app/src/main/res/layout/activity_map_picker.xml`

**Features:**
- Interactive Google Maps integration
- Tap to place marker functionality
- Geocoding to determine address from map coordinates
- Confirm location button

### 3. Payment Method Screen (`PaymentMethodActivity.kt`)

**Description:** This screen allows users to select their preferred payment method and confirm the payment for the pickup order.

**File Location:** `app/src/main/java/com/example/ecotrack/ui/pickup/PaymentMethodActivity.kt`

**Layout:** `app/src/main/res/layout/activity_payment_method.xml`

**Features:**
- Order amount and total display
- Payment method selection (GCash, Cash on Hand)
- PayMongo integration for GCash payments (simulated)
- Confirm payment button

### 4. Order Success Screen (`OrderSuccessActivity.kt`)

**Description:** This screen is displayed after a successful order placement, confirming the order has been received.

**File Location:** `app/src/main/java/com/example/ecotrack/ui/pickup/OrderSuccessActivity.kt`

**Layout:** `app/src/main/res/layout/activity_order_success.xml`

**Features:**
- Success message and icon
- Options to view order status
- Options to view order receipt
- Back to home button

### 5. Order Status Screen (`OrderStatusActivity.kt`)

**Description:** This screen displays the current status of a pickup order, including estimated arrival time when available.

**File Location:** `app/src/main/java/com/example/ecotrack/ui/pickup/OrderStatusActivity.kt`

**Layout:** `app/src/main/res/layout/activity_order_status.xml`

**Features:**
- Real-time order status updates (Processing, Accepted, Completed, Cancelled)
- Order progress visualization
- Order details display (amount, payment method)
- Location display
- Cancel button for processing orders

### 6. Order Receipt Screen (`OrderReceiptActivity.kt`)

**Description:** This screen displays a detailed receipt for a completed pickup order.

**File Location:** `app/src/main/java/com/example/ecotrack/ui/pickup/OrderReceiptActivity.kt`

**Layout:** `app/src/main/res/layout/activity_order_receipt.xml`

**Features:**
- GrabTrash branding
- Receipt number display
- Customer information display
- Order details (amount, tax, total)
- Back to home button

## Model Classes

### 1. `PaymentMethod.kt`

**Description:** Enum class defining available payment methods.

**File Location:** `app/src/main/java/com/example/ecotrack/ui/pickup/model/PaymentMethod.kt`

**Values:**
- GCASH
- CASH_ON_HAND

### 2. `PickupOrder.kt`

**Description:** Data class representing a trash pickup order with all relevant information.

**File Location:** `app/src/main/java/com/example/ecotrack/ui/pickup/model/PickupOrder.kt`

**Properties:**
- id (UUID)
- fullName
- email
- address
- latitude/longitude
- amount, tax, total
- paymentMethod
- status
- createdAt
- estimatedArrival
- referenceNumber

**Methods:**
- getFormattedDate()
- getFormattedTime()
- getFormattedArrivalTime()

### 3. `OrderStatus.kt` (Nested in PickupOrder.kt)

**Description:** Enum class defining possible order status values.

**Values:**
- PROCESSING (Waiting for driver to accept)
- ACCEPTED (Driver has accepted, on the way)
- COMPLETED (Pickup completed)
- CANCELLED (Pickup cancelled)

## Drawable Resources

1. `green_button_background.xml` - Green button with rounded corners
2. `outline_button_background.xml` - Outlined button with rounded corners
3. `edit_text_background.xml` - Light gray background for EditText fields
4. `location_background.xml` - Light gray background for location display
5. `dashed_border.xml` - Dashed border for the receipt number display

## Backend API Documentation

Two documentation files were created for the backend team:

1. `backend_api_endpoints.md` - Comprehensive documentation of all required API endpoints
2. `backend_paymongo_implementation.md` - Detailed implementation guide for PayMongo integration

## PayMongo Integration

The app integrates with PayMongo payment gateway for GCash payments. The implementation includes:

1. Creating a Payment Intent
2. Creating a Payment Method
3. Attaching Payment Method to Payment Intent
4. Handling redirect flow for GCash payments
5. Handling payment callbacks
6. Setting up webhooks for real-time payment updates

In the demo implementation, the PayMongo integration is simulated as the backend services would typically handle the actual API calls.

## Navigation Flow

1. User starts at the Order Pickup screen to enter details
2. User can select a location on the map or edit the pre-filled location
3. User proceeds to Payment Method screen
4. User selects a payment method and confirms payment
5. On successful payment, user is shown the Order Success screen
6. From the success screen, user can:
   - View the order status
   - View the order receipt
   - Return to the home screen

## Implementation Notes

- All UI matches the provided Figma designs
- The app uses Google Maps for location selection
- Order status transitions are simulated in the demo (Processing → Accepted after 5 seconds)
- PayMongo integration code is provided in the backend implementation guide
- Real backend API endpoints are documented for the backend team to implement 