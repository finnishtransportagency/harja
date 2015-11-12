(ns harja.palvelin.integraatiot.api.tyokalut.virheet
  (:use [slingshot.slingshot :only [throw+]]))

(def +invalidi-json+ ::invalidi-json)
(def +viallinen-kutsu+ ::viallinen-kutsu)
(def +sisainen-kasittelyvirhe+ ::sisainen-kasittelyvirhe)

;; Virhekoodit
(def +invalidi-json-koodi+ "invalidi-json")
(def +sisainen-kasittelyvirhe-koodi+ "sisainen-kasittelyvirhe")
(def +ulkoinen-kasittelyvirhe-koodi+ "ulkoinen-kasittelyvirhe")
(def +virheellinen-liite-koodi+ "virheellinen-liite")
(def +tuntematon-urakka-koodi+ "tuntematon-urakka")

;; Virhetyypit
(def +virheellinen-liite+ "virheellinen-liite")
(def +tuntematon-silta+ "tuntematon-silta")
(def +tuntematon-materiaali+ "tuntematon-materiaali")
(def +tuntematon-kayttaja-koodi+ "tuntematon-kayttaja")
(def +tyhja-vastaus+ "tyhja-vastaus")
(def +kayttajalla-puutteelliset-oikeudet+ "kayttajalla-puutteelliset-oikeudet")
(def +puutteelliset-parametrit+ "puutteelliset-parametrit")

(defn heita-poikkeus [tyyppi virheet]
  (throw+
    (let [virheet (if (map? virheet) [virheet] virheet)]
      {:type    tyyppi
       :virheet virheet})))

(defn heita-viallinen-apikutsu-poikkeus [virheet]
  (heita-poikkeus +viallinen-kutsu+ virheet))

(defn heita-sisainen-kasittelyvirhe-poikkeus [virheet]
  (heita-poikkeus +sisainen-kasittelyvirhe-koodi+ virheet))

(defn heita-ulkoinen-kasittelyvirhe-poikkeus [virheet]
  (heita-poikkeus +ulkoinen-kasittelyvirhe-koodi+ virheet))