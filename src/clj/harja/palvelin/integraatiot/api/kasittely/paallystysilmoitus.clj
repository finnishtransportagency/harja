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
            [harja.palvelin.integraatiot.api.kasittely.yllapitokohteet :as yllapitokohteet]
            [harja.palvelin.integraatiot.api.kasittely.tieosoitteet :as tieosoitteet]
            [harja.palvelin.integraatiot.api.tyokalut.parametrit :as parametrit])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(defn- luo-paallystysilmoitus [db kayttaja kohde-id
                               {:keys [perustiedot] :as paallystysilmoitus}
                               valmis-kasiteltavaksi
                               ilmoitustiedot-json]
  (log/debug "Luodaan uusi päällystysilmoitus")
  (q-paallystys/luo-paallystysilmoitus<!
    db
    {:versio 1
     :paallystyskohde kohde-id
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

(defn pura-paallystysilmoitus [vkm db kohteen-tienumero data]
  (let [paallystysilmoitus (:paallystysilmoitus data)
        alustatoimenpiteet (mapv #(assoc-in (:alustatoimenpide %) [:sijainti :numero]
                                            (or (get-in % [:alustatoimenpide :sijainti :numero])
                                                kohteen-tienumero))
                                 (:alustatoimenpiteet paallystysilmoitus))
        alikohteet (mapv #(assoc-in (:alikohde %)
                            [:sijainti :numero]
                            (get-in % [:alikohde :sijainti :numero]
                                    (get-in % [:alikohde :sijainti :tie]
                                            kohteen-tienumero)))
                         (get-in paallystysilmoitus [:yllapitokohde :alikohteet]))
        muunnettava-kohde (-> (:yllapitokohde paallystysilmoitus)
                              (assoc-in [:sijainti :numero] kohteen-tienumero)
                              (assoc :alikohteet alikohteet))
        karttapvm (some-> (get-in muunnettava-kohde [:sijainti :karttapvm])
                          (parametrit/pvm-aika))
        muunnettu-kohde (tieosoitteet/muunna-yllapitokohteen-tieosoitteet
                          vkm
                          db
                          kohteen-tienumero
                          karttapvm muunnettava-kohde)
        muunnetut-alustatoimenpiteet (tieosoitteet/muunna-alustatoimenpiteiden-tieosoitteet
                                       vkm
                                       db
                                       kohteen-tienumero
                                       karttapvm
                                       alustatoimenpiteet)]
    (assoc paallystysilmoitus :yllapitokohde muunnettu-kohde
                              :alustatoimenpiteet muunnetut-alustatoimenpiteet)))

(defn tallenna-paallystysilmoitus [db kayttaja urakka-id kohde paallystysilmoitus valmis-kasiteltavaksi]
  (jdbc/with-db-transaction [db db]
    (let [kohteen-sijainti (get-in paallystysilmoitus [:yllapitokohde :sijainti])
          alikohteet (get-in paallystysilmoitus [:yllapitokohde :alikohteet])]
      (yllapitokohteet/paivita-kohde db (:id kohde) kohteen-sijainti)
      (let [paivitetyt-alikohteet (yllapitokohteet/paivita-alikohteet-paallystysilmoituksesta db kohde alikohteet)
            ;; Päivittyneiden alikohteiden id:t pitää päivittää päällystysilmoituksille
            paallystysilmoitus (assoc-in paallystysilmoitus [:yllapitokohde :alikohteet] paivitetyt-alikohteet)]
        (luo-tai-paivita-paallystysilmoitus db kayttaja urakka-id (:id kohde) paallystysilmoitus valmis-kasiteltavaksi)))))

(defn kirjaa-paallystysilmoitus [vkm db kayttaja urakka-id kohde-id data]
  (let [kohde (first (q-yllapitokohteet/hae-yllapitokohde db {:id kohde-id}))
        kohteen-tienumero (:tr_numero (first (q-yllapitokohteet/hae-kohteen-tienumero db {:kohdeid (:id kohde)})))
        purettu-paallystysilmoitus (pura-paallystysilmoitus vkm db kohteen-tienumero data)
        valmis-kasiteltavaksi (:valmis-kasiteltavaksi data)
        _ (validointi/tarkista-yllapitokohde-kuuluu-urakkaan db urakka-id (:id kohde))
        id (tallenna-paallystysilmoitus db kayttaja urakka-id kohde purettu-paallystysilmoitus valmis-kasiteltavaksi)]
    id))
