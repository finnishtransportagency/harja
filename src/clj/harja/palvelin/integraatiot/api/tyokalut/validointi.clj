(ns harja.palvelin.integraatiot.api.tyokalut.validointi
  "Yleisiä API-kutsuihin liittyviä apufunktioita"
  (:require
    [taoensso.timbre :as log]
    [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
    [harja.kyselyt.urakat :as q-urakat]
    [harja.kyselyt.sopimukset :as q-sopimukset]
    [harja.kyselyt.yllapitokohteet :as q-yllapitokohteet]
    [harja.kyselyt.tieverkko :as q-tieverkko]
    [harja.kyselyt.kayttajat :as kayttajat-q]
    [harja.kyselyt.tietyomaat :as q-tietyomaat]
    [harja.kyselyt.konversio :as konversio]
    [harja.domain.roolit :as roolit]
    [harja.domain.oikeudet :as oikeudet]
    [harja.domain.yllapitokohde :as kohteet]
    [harja.kyselyt.paallystys :as paallystys-q]
    [harja.domain.tierekisteri :as tierekisteri]
    [harja.geo :as geo]
    [cheshire.core :as cheshire]
    [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yy]
    [clojure.string :as clj-str])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(declare tarkista-muut-alikohteet)

(defn tarkista-urakka [db urakka-id]
  (log/debug "Validoidaan urakkaa id:llä" urakka-id)
  (when (not (q-urakat/onko-olemassa? db urakka-id))
    (do
      (let [viesti (format "Urakkaa id:llä %s ei löydy." urakka-id)]
        (log/warn viesti)
        (throw+ {:type virheet/+viallinen-kutsu+
                 :virheet [{:koodi virheet/+tuntematon-urakka-koodi+ :viesti viesti}]})))))

(defn tarkista-sopimus [db urakka-id sopimus-id]
  (log/debug (format "Validoidaan urakan id: %s sopimusta id: %s" urakka-id sopimus-id)
             (when (not (q-sopimukset/onko-olemassa? db urakka-id sopimus-id))
               (do
                 (let [viesti (format "Urakalle id: %s ei löydy sopimusta id: %s." urakka-id sopimus-id)]
                   (log/warn viesti)
                   (throw+ {:type virheet/+viallinen-kutsu+
                            :virheet [{:koodi virheet/+tuntematon-sopimus-koodi+ :viesti viesti}]}))))))

(defn viivageometria-annettu [havainto]
  (when-not (get-in havainto [:havainto :sijainti :viivageometria])
    (throw+ {:type virheet/+viallinen-kutsu+
             :virheet [{:koodi virheet/+sisainen-kasittelyvirhe-koodi+ :viesti "Sanomasta puuttuu viivageometria"}]})))

(defn tarkista-koordinaattien-jarjestys [koordinaatit]
  (let [[x y] (geo/xy koordinaatit)]
    (when (> x y)
      (throw+ {:type virheet/+viallinen-kutsu+
               :virheet [{:koodi virheet/+sisainen-kasittelyvirhe-koodi+ :viesti "Koordinaattien järjestys väärä"}]}))))

(defn tarkista-kayttajan-oikeudet-urakkaan [db urakka-id kayttaja]
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (when (and (not (kayttajat-q/onko-kayttaja-urakan-organisaatiossa? db urakka-id (:id kayttaja)))
             (not (kayttajat-q/onko-kayttajalla-lisaoikeus-urakkaan? db urakka-id (:id kayttaja))))
    (throw+ {:type virheet/+viallinen-kutsu+
             :virheet [{:koodi virheet/+kayttajalla-puutteelliset-oikeudet+
                        :viesti (str "Käyttäjällä: " (:kayttajanimi kayttaja) " ei ole oikeuksia urakkaan: "
                                     urakka-id)}]})))

(defn tarkista-onko-kayttaja-organisaatiossa [db ytunnus kayttaja]
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (when-not (kayttajat-q/onko-kayttaja-organisaatiossa? db ytunnus (:id kayttaja))
    (throw+ {:type virheet/+viallinen-kutsu+
             :virheet [{:koodi virheet/+kayttajalla-puutteelliset-oikeudet+
                        :viesti (str "Käyttäjällä: " (:kayttajanimi kayttaja) " ei ole oikeuksia organisaatioon: " ytunnus)}]})))

(defn tarkista-onko-kayttaja-organisaation-jarjestelma [db ytunnus kayttaja]
  (tarkista-onko-kayttaja-organisaatiossa db ytunnus kayttaja)
  (when (not (:jarjestelma kayttaja))
    (throw+ {:type virheet/+viallinen-kutsu+
             :virheet [{:koodi virheet/+tuntematon-kayttaja-koodi+
                        :viesti (str "Käyttäjä " (:kayttajanimi kayttaja) " ei ole järjestelmä")}]})))

(defn tarkista-urakka-ja-kayttaja [db urakka-id kayttaja]
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (tarkista-urakka db urakka-id)
  (tarkista-kayttajan-oikeudet-urakkaan db urakka-id kayttaja))

(defn tarkista-rooli [kayttaja rooli]
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (when-not (roolit/roolissa? kayttaja rooli)
    (throw+ {:type virheet/+viallinen-kutsu+
             :virheet [{:koodi virheet/+tuntematon-kayttaja-koodi+
                        :viesti (str "Käyttäjällä ei oikeutta resurssiin.")}]})))

(defn tarkista-urakka-sopimus-ja-kayttaja [db urakka-id sopimus-id kayttaja]
  (tarkista-urakka db urakka-id)
  (tarkista-sopimus db urakka-id sopimus-id)
  (tarkista-kayttajan-oikeudet-urakkaan db urakka-id kayttaja))

(defn tarkista-oikeudet-urakan-paivystajatietoihin [db urakka-id kayttaja]
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (when-not (or (roolit/roolissa? kayttaja roolit/liikennepaivystaja)
                (kayttajat-q/onko-kayttaja-urakan-organisaatiossa? db urakka-id (:id kayttaja))
                (kayttajat-q/onko-kayttajalla-lisaoikeus-urakkaan? db urakka-id (:id kayttaja)))

    (throw+ {:type virheet/+viallinen-kutsu+
             :virheet [{:koodi virheet/+kayttajalla-puutteelliset-oikeudet+
                        :viesti (format "Käyttäjällä: %s ei ole oikeuksia urakan: %s päivystäjätietoihin."
                                        (:kayttajanimi kayttaja)
                                        urakka-id)}]})))

(defn tarkista-paallystyskohde
  [db kohde-id urakka-id vuosi tr-osoite ali-ja-muut-kohteet alustatoimet]
  (let [virheviestit (kohteet/validoi-kaikki-backilla db kohde-id urakka-id vuosi tr-osoite ali-ja-muut-kohteet alustatoimet)]
    (when-not (empty? virheviestit)
      (virheet/heita-poikkeus
        virheet/+viallinen-kutsu+
        [{:koodi :viallisia-tieosia
          :viesti (loop [[[otsikko virheet] & loput-virheet] (sequence virheviestit)
                         muodostettu-viesti ""]
                    (if (nil? otsikko)
                      muodostettu-viesti
                      (let [otsikko (str "-----------\n" (clj-str/capitalize (name otsikko)) "\n")
                            virheteksti (apply str (interpose "\n"
                                                              (distinct
                                                               (mapcat (fn [virheteksti-map]
                                                                         (-> virheteksti-map vals flatten))
                                                                       virheet))))]
                        (recur loput-virheet
                               (if-not (empty? virheteksti)
                                 (str muodostettu-viesti otsikko (str virheteksti "\n"))
                                 muodostettu-viesti)))))}]))))

(defn tarkista-tietyomaa [db id jarjestelma]
  (when (not (q-tietyomaat/onko-olemassa? db {:id id :jarjestelma jarjestelma}))
    (do
      (let [viesti (format "Suljettua tieosuutta (id: %s) ei löydy" id)]
        (log/warn viesti)
        (virheet/heita-poikkeus
          virheet/+viallinen-kutsu+
          [{:koodi virheet/+tuntematon-yllapitokohde+ :viesti viesti}])))))

(defn tarkista-saako-kohteen-paivittaa [db kohde-id]
  (when (paallystys-q/onko-olemassa-paallystysilmoitus? db kohde-id)
    (do
      (let [viesti (format "Kohteelle (id: %s) on jo kirjattu päällystysilmoitus. Päivitys ei ole sallittu" kohde-id)]
        (log/warn viesti)
        (virheet/heita-poikkeus
          virheet/+viallinen-kutsu+
          [{:koodi virheet/+lukittu-yllapitokohde+ :viesti viesti}])))))

(defn tarkista-yllapitokohde-kuuluu-urakkatyypin-mukaiseen-urakkaan
  "Tarkistaa, että ylläpitokohde kuuluu annettuun urakkaan suoraan tai on merkitty suoritettavaksi
   tiemerkintäurakaksi."
  [db urakka-id urakan-tyyppi kohde-id]
  (let [urakan-kohteet (case urakan-tyyppi
                         :paallystys
                         (q-yllapitokohteet/hae-urakkaan-kuuluvat-yllapitokohteet db {:urakka urakka-id})
                         :tiemerkinta
                         (q-yllapitokohteet/hae-urakkaan-liittyvat-tiemerkintakohteet db {:urakka urakka-id}))]
    (when-not (some #(= kohde-id %) (map :id urakan-kohteet))
      (virheet/heita-poikkeus virheet/+viallinen-kutsu+
                              {:koodi virheet/+urakkaan-kuulumaton-yllapitokohde+
                               :viesti "Ylläpitokohde ei kuulu urakkaan."}))))

(defn tarkista-yllapitokohde-kuuluu-urakkaan [db urakka-id kohde-id]
  (log/debug (format "Validoidaan urakan (id: %s) kohdetta (id: %s)" urakka-id kohde-id))
  (when (not (q-yllapitokohteet/onko-olemassa-urakalla? db {:urakka urakka-id :kohde kohde-id}))
    (do
      (let [viesti (format "Urakalla (id: %s) ei ole kohdetta (id: %s)." urakka-id kohde-id)]
        (log/warn viesti)
        (virheet/heita-poikkeus
          virheet/+viallinen-kutsu+
          [{:koodi virheet/+tuntematon-yllapitokohde+ :viesti viesti}])))))

(defn tarkista-onko-liikenneviraston-jarjestelma [db kayttaja]
  (when-not (kayttajat-q/liikenneviraston-jarjestelma? db (:kayttajanimi kayttaja))
    (log/error (format "Kayttajatunnus %s ei ole Väylän järjestelmä eikä sillä ole oikeutta kutsuttuun resurssiin."
                       (:kayttajatunnus kayttaja)))
    (throw+ {:type virheet/+kayttajalla-puutteelliset-oikeudet+
             :virheet [{:koodi virheet/+kayttajalla-puutteelliset-oikeudet+
                        :viesti "Käyttäjällä ei resurssiin."}]})))

(defn tarkista-urakkatyyppi [urakkatyyppi]
  (when urakkatyyppi
    (let [urakkatyypit #{"hoito"
                         "paallystys"
                         "paikkaus"
                         "tiemerkinta"
                         "valaistus"
                         "siltakorjaus"
                         "tekniset-laitteet"
                         "vesivayla-hoito"
                         "vesivayla-ruoppaus"
                         "vesivayla-turvalaitteiden-korjaus"
                         "vesivayla-kanavien-hoito"
                         "vesivayla-kanavien-korjaus"}]
      (when (not (contains? urakkatyypit urakkatyyppi))
        (virheet/heita-viallinen-apikutsu-poikkeus
          {:koodi virheet/+puutteelliset-parametrit+
           :viesti (format "Tuntematon urakkatyyppi: %s" urakkatyyppi)})))))
