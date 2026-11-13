const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 1000);
}

function validProfile(uid) {
  const now = pastTimestamp();
  return {
    userId: uid,
    displayName: "Alice Polyglot",
    activeCityId: "paris",
    difficulty: "beginner",
    preferredModel: "gemini-4-hydrogen",
    xp: 120,
    streakDays: 3,
    lastPracticeDate: "2026-11-11",
    completedNpcIds: ["paris_camille"],
    createdAt: now,
    updatedAt: now,
  };
}

function validPhrase(uid) {
  const now = pastTimestamp();
  return {
    userId: uid,
    cityId: "paris",
    npcName: "Camille",
    targetText: "Un croissant, s'il vous plaît",
    phonetic: "uhn krwah-sahn seel voo pleh",
    translation: "A croissant, please",
    culturalNote: "Always greet the baker with Bonjour before ordering.",
    illustrationCommands: ["BG|#FFF8EE|#F4A236", "OVAL|0.5|0.5|0.4|0.25|#D9822B|filled"],
    masteryLevel: 2,
    createdAt: now,
    updatedAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated caller cannot read or write user profiles or phrases", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("phrases").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID)));
});

test("Owner can create, read, and update their own UserProfile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);
  await assertSucceeds(profileRef.set(validProfile(ALICE_UID)));
  await assertSucceeds(profileRef.get());
  await assertSucceeds(
    profileRef.update({
      xp: 240,
      streakDays: 4,
      lastPracticeDate: "2026-11-12",
      activeCityId: "tokyo",
      updatedAt: pastTimestamp(),
    })
  );
});

test("Shadow update with extra field is rejected on UserProfile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);
  await assertSucceeds(profileRef.set(validProfile(ALICE_UID)));
  await assertFails(
    profileRef.update({
      xp: 999,
      isAdmin: true,
      updatedAt: pastTimestamp(),
    })
  );
});

test("Cross-user access to profile and phrases is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("phrases").doc("phrase_1").set(validPhrase(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("phrases").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("phrases").doc("phrase_2").set(validPhrase(BOB_UID))
  );
});

test("Owner can create, list, update masteryLevel, and delete SavedPhrase", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const phrasesCol = aliceDb.collection("users").doc(ALICE_UID).collection("phrases");
  const phraseRef = phrasesCol.doc("phrase_croissant");

  await assertSucceeds(phraseRef.set(validPhrase(ALICE_UID)));
  await assertSucceeds(phrasesCol.get());
  await assertSucceeds(
    phraseRef.update({
      masteryLevel: 5,
      updatedAt: pastTimestamp(),
    })
  );
  await assertSucceeds(phraseRef.delete());
});

test("Invalid cityId or negative xp is rejected by schema rules", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const badProfile = { ...validProfile(ALICE_UID), xp: -10 };
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).set(badProfile));

  const badPhrase = { ...validPhrase(ALICE_UID), cityId: "mars" };
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("phrases").doc("bad_1").set(badPhrase)
  );
});
