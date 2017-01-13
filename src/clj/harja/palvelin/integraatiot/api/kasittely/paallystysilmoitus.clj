(ns harja.palvelin.integraatiot.api.kasittely.paallystysilmoitus
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [dekoodaa-base64]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [tee-kirjausvastauksen-body]]
            [harja.kyselyt.yllapitokohteet :as q-yllapitokohteet]
            [taoensso.timbre :as log]
            [harja.kyselyt.paallystys :as q-paallystys]
            [harja.palvelin.integraatiot.api.sanomat.paallystysilmoitus :as paallystysilmoitussanoma]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json]
            [harja.domain.paallystysilmoitus :as paallystysilmoitus-domain]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.api.kasittely.yllapitokohteet :as yllapitokohteet])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(defn- luo-paallystysilmoitus [db kayttaja kohde-id
                               {:keys [perustiedot] :as paallystysilmoitus}
                               valmis-kasiteltavaksi
                               ilmoitustiedot-json]
  (log/debug "Luodaan uusi päällystysilmoitus")
  (q-paallystys/luo-paallystysilmoitus<!
    db
    {:paallystyskohde kohde-id
     :tila (paallystysilmoitus-domain/paattele-ilmoituksen-tila
             valmis-kasiteltavaksi false)
     :ilmoitustiedot ilmoitustiedot-json
     :takuupvm (json/aika-string->java-sql-date
                 (:takuupvm perustiedot))
     :kayttaja (:id kayttaja)}))

(defn- paivita-paallystysilmoitus [db kayttaja urakka-id kohde-id
                                   {:keys [perustiedot] :as paallystysilmoitus}
                                   paallystysilmoitus-kannassa
                                   valmis-kasiteltavaksi
                                   ilmoitustiedot-json]
  (log/debug "Päivitetään vanha päällystysilmoitus")
  (if (not= (:tila paallystysilmoitus-kannassa) "lukittu")
    (q-paallystys/paivita-paallystysilmoitus<!
      db
      {:tila (if (and (= (:tila paallystysilmoitus-kannassa) "aloitettu")
                      (true? valmis-kasiteltavaksi))
               "valmis"
               (:tila paallystysilmoitus-kannassa)) ;; Ei päivitetä
       :ilmoitustiedot ilmoitustiedot-json
       :takuupvm (json/aika-string->java-sql-date
                   (:takuupvm perustiedot))
       :muokkaaja (:id kayttaja)
       :id kohde-id
       :urakka urakka-id})
    (virheet/heita-poikkeus virheet/+paallystysilmoitus-lukittu+
                            {:koodi virheet/+paallystysilmoitus-lukittu+
                             :viesti "Päällystysilmoitus on lukittu."})))

(defn luo-tai-paivita-paallystysilmoitus [db kayttaja urakka-id kohde-id paallystysilmoitus valmis-kasiteltavaksi]
  (let [ilmoitustiedot-json (paallystysilmoitussanoma/rakenna paallystysilmoitus)
        paallystysilmoitus-kannassa (first (q-paallystys/hae-paallystysilmoitus-paallystyskohteella db {:paallystyskohde kohde-id}))
        muokattu-paallystysilmoitus (if (:id paallystysilmoitus-kannassa)
                                      (paivita-paallystysilmoitus db kayttaja urakka-id kohde-id
                                                                  paallystysilmoitus
                                                                  paallystysilmoitus-kannassa
                                                                  valmis-kasiteltavaksi
                                                                  ilmoitustiedot-json)
                                      (luo-paallystysilmoitus db kayttaja kohde-id paallystysilmoitus valmis-kasiteltavaksi ilmoitustiedot-json))]
    (:id muokattu-paallystysilmoitus)))

(defn pura-paallystysilmoitus [data]
  (-> (:paallystysilmoitus data)
      (assoc :alikohteet (mapv :alikohde (get-in data [:paallystysilmoitus :yllapitokohde :alikohteet])))
      (assoc :alustatoimenpiteet (mapv :alustatoimenpide (get-in data [:paallystysilmoitus :alustatoimenpiteet])))))

(defn validoi-paallystysilmoitus [db urakka-id kohde paallystysilmoitus]
  (validointi/tarkista-urakan-kohde db urakka-id (:id kohde))
  (let [kohteen-sijainti (get-in paallystysilmoitus [:yllapitokohde :sijainti])
        alikohteet (:alikohteet paallystysilmoitus)
        alustatoimenpiteet (:alustatoimenpiteet paallystysilmoitus)
        kohteen-tienumero (:tr-numero kohde)]
    (validointi/tarkista-paallystysilmoitus db (:id kohde) kohteen-tienumero kohteen-sijainti alikohteet alustatoimenpiteet)))

(defn tallenna-paallystysilmoitus [db kayttaja urakka-id kohde paallystysilmoitus valmis-kasiteltavaksi]
  (let [kohteen-sijainti (get-in paallystysilmoitus [:yllapitokohde :sijainti])
        alikohteet (:alikohteet paallystysilmoitus)]
    (yllapitokohteet/paivita-kohde db (:id kohde) kohteen-sijainti)
    (let [paivitetyt-alikohteet (yllapitokohteet/paivita-alikohteet db kohde alikohteet)
          ;; Päivittyneiden alikohteiden id:t pitää päivittää päällystysilmoituksille
          paallystysilmoitus (assoc-in paallystysilmoitus [:yllapitokohde :alikohteet] paivitetyt-alikohteet)]
      (luo-tai-paivita-paallystysilmoitus db kayttaja urakka-id (:id kohde) paallystysilmoitus valmis-kasiteltavaksi))))


(defn kirjaa-paallystysilmoitus [db kayttaja urakka-id kohde-id data]
  (jdbc/with-db-transaction
    [db db]
    (let [purettu-paallystysilmoitus (pura-paallystysilmoitus data)
          valmis-kasiteltavaksi (:valmis-kasiteltavaksi data)
          kohde (first (q-yllapitokohteet/hae-yllapitokohde db {:id kohde-id}))
          _ (validoi-paallystysilmoitus db urakka-id kohde purettu-paallystysilmoitus)
          id (tallenna-paallystysilmoitus db kayttaja urakka-id kohde
                                          purettu-paallystysilmoitus valmis-kasiteltavaksi)]
      id)))
