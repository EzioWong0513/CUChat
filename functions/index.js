const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

// Cloud function to send FCM notification
exports.sendNotification = functions.https.onCall(async (data, context) => {
  // Verify authentication
  if (!context.auth) {
    throw new functions.https.HttpsError(
        "unauthenticated",
        "The function must be called while authenticated.",
    );
  }

  try {
    const {receiverId, title, body, clickAction, additionalData} = data;

    // Get the receiver's FCM token from Firestore
    const userDoc = await admin.firestore().collection("users").doc(receiverId).get();

    if (!userDoc.exists) {
      throw new functions.https.HttpsError(
          "not-found",
          "The specified user does not exist.",
      );
    }

    const fcmToken = userDoc.data().fcmToken;

    if (!fcmToken) {
      throw new functions.https.HttpsError(
          "failed-precondition",
          "The specified user does not have an FCM token.",
      );
    }

    // Create the notification message
    const message = {
      token: fcmToken,
      notification: {
        title: title,
        body: body,
      },
      data: {
        click_action: clickAction || "MAIN_ACTIVITY",
        ...additionalData,
      },
      android: {
        priority: "high",
        notification: {
          sound: "default",
          default_sound: true,
          default_vibrate_timings: true,
          default_light_settings: true,
        },
      },
      apns: {
        payload: {
          aps: {
            sound: "default",
          },
        },
      },
    };

    // Send the message
    const response = await admin.messaging().send(message);
    console.log("Successfully sent message:", response);

    return {success: true, messageId: response};
  } catch (error) {
    console.error("Error sending notification:", error);
    throw new functions.https.HttpsError("internal", error.message);
  }
});

// Cloud function to send notification when a new message is added
exports.sendChatNotification = functions.firestore
    .document("chats/{chatId}/messages/{messageId}")
    .onCreate(async (snapshot, context) => {
      try {
        const message = snapshot.data();
        const {chatId} = context.params;

        // Only send notification if the receiver is not the sender
        const senderId = message.senderId;
        const receiverId = message.receiverId;

        // Get the chat document to check if the user is online
        const chatDoc = await admin.firestore().collection("chats").doc(chatId).get();

        if (!chatDoc.exists) {
          console.log("Chat document does not exist");
          return null;
        }

        // Get receiver's user document to check online status and get FCM token
        const receiverDoc = await admin.firestore().collection("users").doc(receiverId).get();

        if (!receiverDoc.exists) {
          console.log("Receiver document does not exist");
          return null;
        }

        const receiverData = receiverDoc.data();

        // Don't send notification if the receiver is online and in the chat
        if (receiverData.isOnline) {
        // You'd need a field in Firestore to track if user is currently in this specific chat
        // For simplicity, we'll just check if they're online
          console.log("Receiver is online, not sending notification");
          return null;
        }

        // Get the sender's information
        const senderDoc = await admin.firestore().collection("users").doc(senderId).get();

        if (!senderDoc.exists) {
          console.log("Sender document does not exist");
          return null;
        }

        const senderData = senderDoc.data();
        const senderName = senderData.username || "Someone";

        // Create the notification message
        const notificationMessage = {
          token: receiverData.fcmToken,
          notification: {
            title: senderName,
            body: message.content,
          },
          data: {
            click_action: "CHAT_ACTIVITY",
            chat_id: chatId,
            user_id: senderId,
          },
          android: {
            priority: "high",
            notification: {
              sound: "default",
              default_sound: true,
              default_vibrate_timings: true,
              default_light_settings: true,
            },
          },
          apns: {
            payload: {
              aps: {
                sound: "default",
              },
            },
          },
        };

        // Send the message
        const response = await admin.messaging().send(notificationMessage);
        console.log("Successfully sent message:", response);

        return null;
      } catch (error) {
        console.error("Error sending chat notification:", error);
        return null;
      }
    });

// Cloud function to send notification when a user is nearby
exports.sendNearbyUserNotification = functions.firestore
    .document("users/{userId}/nearbyUsers/{nearbyUserId}")
    .onCreate(async (snapshot, context) => {
      try {
        const {userId, nearbyUserId} = context.params;

        // Get the user documents
        const userDoc = await admin.firestore().collection("users").doc(userId).get();
        const nearbyUserDoc = await admin.firestore().collection("users").doc(nearbyUserId).get();

        if (!userDoc.exists || !nearbyUserDoc.exists) {
          console.log("User document does not exist");
          return null;
        }

        const userData = userDoc.data();
        const nearbyUserData = nearbyUserDoc.data();

        // Create the notification message
        const notificationMessage = {
          token: userData.fcmToken,
          notification: {
            title: "Someone is nearby!",
            body: `${nearbyUserData.username} is now in your vicinity`,
          },
          data: {
            click_action: "MAP_FRAGMENT",
            user_id: nearbyUserId,
          },
        };

        // Send the message
        await admin.messaging().send(notificationMessage);

        return null;
      } catch (error) {
        console.error("Error sending nearby user notification:", error);
        return null;
      }
    });
