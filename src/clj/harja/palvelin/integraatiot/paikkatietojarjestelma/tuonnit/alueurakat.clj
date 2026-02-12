(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.alueurakat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.urakat :as u]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [clojure.string :as str]))

(defn string-intiksi [str]
  (if (string? str)
    (let [ei-numeeriset-poistettu (re-find #"\d+" str)]
      (if (nil? ei-numeeriset-poistettu)
        nil
        (Integer. ei-numeeriset-poistettu)))
    str))

(defn- normalisoi-numerinen-avain
  "Normalisoi numerisen avaimen: poistaa etunollat ja whitespacen.
   Jos avain ei ole puhtaasti numerinen, palauttaa vain trimmatun version."
  [avain]
  (when avain
    (let [trimmattu (str/trim avain)]
      ;; Poistetaan etunollat vain jos avain koostuu pelkästään numeroista
      (if (re-matches #"\d+" trimmattu)
        (let [ilman-etunollia (str/replace trimmattu #"^0+" "")]
          ;; Jos poistettiin kaikki, palauta "0"
          (if (empty? ilman-etunollia)
            "0"
            ilman-etunollia))
        trimmattu))))

(defn- resolvoi-urakkanumero
  "Resolvoi shapefile-avaimen (sampoid tai numerinen) urakkanumeroksi.
   Palauttaa urakkanumeron tai nil jos resolvointia ei onnistuttu."
  [db avain]
  (when avain
    (let [avain-str (str/trim (str avain))
          normalisoitu (normalisoi-numerinen-avain avain-str)]
      ;; Yritä ensin suoraa matchausta urakkanumerolla
      (or
        (when normalisoitu
          (when (u/hae-urakka-id-alueurakkanumerolla db {:alueurakka normalisoitu})
            normalisoitu))
        ;; Jos suora match ei toimi, yritä sampoidin kautta
        (let [urakka (first (u/hae-id-sampoidlla db avain-str))]
          (when urakka
            (let [urakka-tiedot (first (u/hae-urakan-alueurakkanumero db (:id urakka)))]
              (:alueurakkanro urakka-tiedot))))))))

(defn luo-tai-paivita-urakka [db urakka]
  (let [raaka-avain (or (:urakkakood urakka) (:gridcode urakka))
        urakkanumero (resolvoi-urakkanumero db raaka-avain)
        geometria (.toString (:the_geom urakka))
        piirinumero (string-intiksi (or (:piirinro urakka) (:elynro urakka)))
        elynimi (or (:elyn_nimi urakka) "")
        nimi (or (:urakka_nim urakka) "")]
    (when-not urakkanumero
      (virheet/heita-poikkeus 
        virheet/+puutteellinen-paikkatietoaineisto+
        [{:koodi virheet/+tuntematon-urakka-koodi+
          :viesti (format "Alueurakka-avainta '%s' ei voitu resolvoida urakkanumeroksi. Tarkista että urakka on olemassa ja aineisto on oikein." 
                          raaka-avain)}]))
    (let [olemassa (first (u/hae-alueurakka-numerolla db urakkanumero))]
      (if olemassa
        (u/paivita-alueurakka! db geometria piirinumero elynimi nimi urakkanumero)
        (u/luo-alueurakka<! db urakkanumero geometria piirinumero elynimi nimi)))
    (u/paivita-alue-urakalle! db geometria urakkanumero)))

(defn vie-urakka-entry [db urakka]
  (if (:the_geom urakka)
    (luo-tai-paivita-urakka db urakka)
    (virheet/heita-poikkeus virheet/+puutteellinen-paikkatietoaineisto+
                    [{:koodi virheet/+puuttuva-geometria-alueurakassa+
                              :viesti (format "Alueurakasta (id: %s.) puuttuu geometria. Tarkista aineisto. Alueurakoita ei päivitetä lainkaan." (:urakka_nimi urakka))}])))

(defn vie-urakat-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan urakat kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]
                                (u/tuhoa-alueurakkadata! db)
                                (doseq [urakka (shapefile/tuo shapefile)]
                                  (vie-urakka-entry db urakka)))
      (u/paivita-urakka-alueiden-nakyma db)
      (log/debug "Alueurakoiden tuonti kantaan valmis."))
    (log/debug "Alueurakoiden tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
