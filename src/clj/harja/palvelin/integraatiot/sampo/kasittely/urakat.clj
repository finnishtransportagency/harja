(ns harja.palvelin.integraatiot.sampo.kasittely.urakat
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot-q]
            [harja.kyselyt.sopimukset :as sopimukset]
            [harja.kyselyt.toimenpideinstanssit :as toimenpiteet-q]
            [harja.kyselyt.organisaatiot :as organisaatiot-q]
            [harja.palvelin.integraatiot.sampo.kasittely.urakkatyyppi :as urakkatyyppi]
            [harja.palvelin.integraatiot.sampo.sanomat.kuittaus-sampoon-sanoma :as kuittaus-sanoma]
            [harja.palvelin.integraatiot.sampo.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.sampo.kasittely.maksuerat :as maksuerat]
            [harja.tyokalut.merkkijono :as merkkijono]
            [harja.palvelin.integraatiot.sampo.kasittely.valitavoitteet :as valitavoitteet]
            [clojure.string :as str])
  (:use [slingshot.slingshot :only [throw+]]))

(defn pudota-etunollat [alueurakkanumero]
  (str/replace alueurakkanumero #"^0+" ""))

(defn pura-alueurakkanro [urakka-sampoid alueurakkanro]
  (let [tarkista-alueurakkanro #(if (merkkijono/kokonaisluku? %)
                                  %
                                  (log/error (format "Sampon urakan (id: %s) alueurakkanumero (%s) ei ole validi numero.
                                                    Alueurakkanumero täytyy korjata Sampoon."
                                                     urakka-sampoid alueurakkanro)))
        osat (str/split alueurakkanro #"-")]
    (if (= 2 (count osat))
      {:tyypit (first osat) :alueurakkanro (tarkista-alueurakkanro (second osat))}
      (do
        (log/error (format "Sampon urakan (id: %s) alueurakkanumeroa (%s) ei voitu parsia.
                            Alueurakkanumero täytyy korjata Sampoon."
                           urakka-sampoid alueurakkanro))
        {:tyypit nil :alueurakkanro (tarkista-alueurakkanro alueurakkanro)}))))

(defn- paivita-urakka [db nimi alkupvm loppupvm hanke-sampo-id urakka-id alueurakkanro urakkatyyppi sopimustyyppi
                       hallintayksikko urakoitsija-id]
  (log/debug "Päivitetään urakka, jonka id on: " urakka-id ".")
  (urakat-q/paivita-urakka! db nimi alkupvm loppupvm hanke-sampo-id urakkatyyppi hallintayksikko sopimustyyppi
                            alueurakkanro urakoitsija-id urakka-id))

(defn- luo-urakka [db nimi alkupvm loppupvm hanke-sampo-id sampo-id alueurakkanro urakkatyyppi sopimustyyppi
                   hallintayksikko urakoitsija-id]
  (log/debug "Luodaan uusi urakka.")

  (let [uusi-id (:id (urakat-q/luo-urakka<! db nimi alkupvm loppupvm hanke-sampo-id sampo-id urakkatyyppi hallintayksikko
                                            sopimustyyppi alueurakkanro urakoitsija-id))]
    (log/debug "Uusi urakka id on:" uusi-id)
    (urakat-q/paivita-urakka-alueiden-nakyma db)
    uusi-id))

(defn- tallenna-urakka [db sampo-id nimi alkupvm loppupvm hanke-sampo-id urakkanro urakkatyyppi sopimustyyppi
                        ely-id urakoitsija-id]
  (let [urakka-id (:id (first (urakat-q/hae-id-sampoidlla db sampo-id)))]
    (if urakka-id
      (do
        (paivita-urakka db nimi alkupvm loppupvm hanke-sampo-id urakka-id urakkanro urakkatyyppi sopimustyyppi
                        ely-id urakoitsija-id)
        urakka-id)
      (do
        (luo-urakka db nimi alkupvm loppupvm hanke-sampo-id sampo-id urakkanro urakkatyyppi sopimustyyppi
                    ely-id urakoitsija-id)))
    (case urakkatyyppi
      "valaistus" (urakat-q/paivita-valaistusurakan-geometria-kannasta! db urakkanro)
      "tekniset-laitteet" (urakat-q/paivita-tekniset-laitteet-urakan-geometria-kannasta! db urakkanro)
      "hoito"     (urakat-q/paivita-alueurakan-geometria-kannasta! db urakkanro))))

(defn- paivita-yhteyshenkilo [db yhteyshenkilo-sampo-id urakka-id]
  (yhteyshenkilot-q/irrota-sampon-yhteyshenkilot-urakalta! db urakka-id)
  (yhteyshenkilot-q/liita-sampon-yhteyshenkilo-urakkaan<! db yhteyshenkilo-sampo-id urakka-id))

(defn- paivita-sopimukset [db urakka-sampo-id]
  (sopimukset/paivita-urakka-sampoidlla! db urakka-sampo-id))

(defn- paivita-toimenpiteet [db urakka-sampo-id]
  (toimenpiteet-q/paivita-urakka-sampoidlla! db urakka-sampo-id))

(defn- yllapito-urakka? [urakkatyyppi]
  ;; Samposta ei tuoda paikkausurakoita
  (some? (#{"paallystys" "tiemerkinta" "valaistus"} urakkatyyppi)))

(defn- luo-yllapidon-toimenpiteet [db {:keys [urakka-id urakkatyyppi alkupvm loppupvm]}]
  (when (yllapito-urakka? urakkatyyppi)
    (log/debug "Luodaan " urakkatyyppi "-urakalle toimenpideinstanssi")
    (let [yllapidon-3-tason-toimenpidekoodit {"paallystys" "PAAL_YKSHINT"
                                              "tiemerkinta" "TIEM_YKSHINT"
                                              "valaistus" "VALA_YKSHINT"}
          toimenpidekoodi (yllapidon-3-tason-toimenpidekoodit urakkatyyppi)]
      (when (and toimenpidekoodi (not (toimenpiteet-q/onko-urakalla-toimenpide? db urakka-id toimenpidekoodi)))
        (toimenpiteet-q/luo-yllapidon-toimenpideinstanssi<! db
                                                            toimenpidekoodi
                                                            alkupvm
                                                            loppupvm
                                                            urakka-id)))))

(defn paattele-sopimustyyppi [urakkatyyppi]
  (when (= urakkatyyppi "paallystys")
    "kokonaisurakka"))

(defn hae-urakoitsija [db ytunnus nimi]
  (when-let [ytunnus (if (empty? ytunnus) nil (merkkijono/leikkaa 9 ytunnus))]
    (:id (or
           (first (organisaatiot-q/hae-id-y-tunnuksella db ytunnus))
           (organisaatiot-q/luo-organisaatio<! db nil nimi ytunnus nil nil "urakoitsija")))))

(defn kasittele-urakka [db {:keys [viesti-id sampo-id nimi alkupvm loppupvm hanke-sampo-id yhteyshenkilo-sampo-id
                                   ely-hash alueurakkanro urakoitsijan-nimi urakoitsijan-ytunnus]}]
  (log/debug "Käsitellään urakka Sampo id:llä: " sampo-id)
  (try
    (when (not (urakat-q/perustettu-harjassa? db sampo-id))
      (let [urakoitsija-id (hae-urakoitsija db urakoitsijan-ytunnus urakoitsijan-nimi)
            tyyppi-ja-alueurakkanro (pura-alueurakkanro sampo-id alueurakkanro)
            tyypit (:tyypit tyyppi-ja-alueurakkanro)
            alueurakkanro (pudota-etunollat (:alueurakkanro tyyppi-ja-alueurakkanro))
            urakkatyyppi (urakkatyyppi/paattele-urakkatyyppi tyypit)
            sopimustyyppi (paattele-sopimustyyppi urakkatyyppi)
            ely-id (:id (first (organisaatiot-q/hae-ely-id-sampo-hashilla db (merkkijono/leikkaa 5 ely-hash))))
            urakka-id (tallenna-urakka db sampo-id nimi alkupvm loppupvm hanke-sampo-id alueurakkanro urakkatyyppi
                                       sopimustyyppi ely-id urakoitsija-id)]
        (log/debug (format "Käsiteltävän urakan id on: %s, tyyppi: %s, alueurakkanro: %s"
                           urakka-id
                           urakkatyyppi
                           alueurakkanro))
        (urakat-q/paivita-hankkeen-tiedot-urakalle! db hanke-sampo-id)
        (paivita-yhteyshenkilo db yhteyshenkilo-sampo-id urakka-id)
        (paivita-sopimukset db sampo-id)
        (paivita-toimenpiteet db sampo-id)
        (maksuerat/perusta-maksuerat-hoidon-urakoille db)
        (luo-yllapidon-toimenpiteet db {:urakka-id urakka-id
                                        :urakkatyyppi urakkatyyppi
                                        :alkupvm alkupvm
                                        :loppupvm loppupvm})
        (valitavoitteet/kasittele-urakan-valitavoitteet db sampo-id)

        (log/debug "Urakka käsitelty onnistuneesti")
        (kuittaus-sanoma/muodosta-onnistunut-kuittaus viesti-id "Project")))
    (catch Exception e
      (log/error e "Tapahtui poikkeus tuotaessa urakkaa Samposta (Sampo id:" sampo-id ", viesti id:" viesti-id ").")
      (let [kuittaus (kuittaus-sanoma/muodosta-muu-virhekuittaus viesti-id "Project" "Internal Error")]
        (throw+ {:type virheet/+poikkeus-samposisaanluvussa+
                 :kuittaus kuittaus
                 :virheet [{:poikkeus e}]})))))

(defn kasittele-urakat [db urakat]
  (mapv #(kasittele-urakka db %) urakat))
