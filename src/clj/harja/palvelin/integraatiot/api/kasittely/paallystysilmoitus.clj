(ns harja.palvelin.integraatiot.api.kasittely.paallystysilmoitus
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [dekoodaa-base64]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [tee-kirjausvastauksen-body]]
            [harja.kyselyt.yllapitokohteet :as q-yllapitokohteet]
            [taoensso.timbre :as log]
            [harja.kyselyt.paallystys :as q-paallystys]
            [harja.kyselyt.tieverkko :as q-tieverkko]
            [harja.palvelin.integraatiot.api.sanomat.paallystysilmoitus :as paallystysilmoitussanoma]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json]
            [harja.domain.paallystysilmoitus :as paallystysilmoitus-domain]
            [harja.domain.skeema :as skeema])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(defn paivita-alikohteet [db kohde alikohteet]
  (q-yllapitokohteet/poista-yllapitokohteen-kohdeosat! db {:id (:id kohde)})
  (mapv
    (fn [alikohde]
      (let [sijainti (:sijainti alikohde)
            osoite {:tie (:tr-numero kohde)
                    :aosa (:aosa sijainti)
                    :aet (:aet sijainti)
                    :losa (:losa sijainti)
                    :loppuet (:let sijainti)}
            sijainti-geometria (q-tieverkko/tierekisteriosoite-viivaksi db osoite)
            parametrit {:yllapitokohde (:id kohde)
                        :nimi (:nimi alikohde)
                        :tunnus (:tunnus alikohde)
                        :tr_numero (:tr-numero kohde)
                        :tr_alkuosa (:aosa sijainti)
                        :tr_alkuetaisyys (:aet sijainti)
                        :tr_loppuosa (:losa sijainti)
                        :tr_loppuetaisyys (:let sijainti)
                        :tr_ajorata (:tr-ajorata kohde)
                        :tr_kaista (:tr-kaista kohde)
                        :toimenpide (:toimenpide alikohde)
                        :sijainti sijainti-geometria}]
        (assoc alikohde :id (:id (q-yllapitokohteet/luo-yllapitokohdeosa<! db parametrit)))))
    alikohteet))

(defn paivita-kohde [db kohde-id kohteen-sijainti]
  (q-yllapitokohteet/paivita-yllapitokohteen-sijainti!
    db (assoc (clojure.set/rename-keys
                kohteen-sijainti
                {:aosa :tr_alkuosa
                 :aet :tr_alkuetaisyys
                 :losa :tr_loppuosa
                 :let :tr_loppuetaisyys})
         :id
         kohde-id)))

(defn- luo-paallystysilmoitus [db kayttaja kohde-id
                               {:keys [perustiedot] :as paallystysilmoitus}
                               ilmoitustiedot-json]
  (log/debug "Luodaan uusi päällystysilmoitus")
  (q-paallystys/luo-paallystysilmoitus<!
    db
    {:paallystyskohde kohde-id
     :tila (paallystysilmoitus-domain/paattele-ilmoituksen-tila perustiedot)
     :ilmoitustiedot ilmoitustiedot-json
     :aloituspvm (json/aika-string->java-sql-date (:aloituspvm perustiedot))
     :valmispvm_paallystys (json/aika-string->java-sql-date
                             (:valmispvm-paallystys perustiedot))
     :valmispvm_kohde (json/aika-string->java-sql-date
                        (:valmispvm-kohde perustiedot))
     :takuupvm (json/aika-string->java-sql-date
                 (:takuupvm perustiedot))
     :muutoshinta (paallystysilmoitus-domain/laske-muutokset-kokonaishintaan
                    (:tyot paallystysilmoitus))
     :kayttaja (:id kayttaja)}))

(defn- paivita-paallystysilmoitus [db kayttaja urakka-id kohde-id
                                   {:keys [perustiedot] :as paallystysilmoitus}
                                   ilmoitustiedot-json]
  (log/debug "Päivitetään vanha päällystysilmoitus")
  (q-paallystys/paivita-paallystysilmoitus<!
    db
    {:tila (paallystysilmoitus-domain/paattele-ilmoituksen-tila perustiedot)
     :ilmoitustiedot ilmoitustiedot-json
     :aloituspvm (json/aika-string->java-sql-date (:aloituspvm perustiedot))
     :valmispvm_paallystys (json/aika-string->java-sql-date
                             (:valmispvm-paallystys perustiedot))
     :valmispvm_kohde (json/aika-string->java-sql-date
                        (:valmispvm-kohde perustiedot))
     :takuupvm (json/aika-string->java-sql-date
                 (:takuupvm perustiedot))
     :muutoshinta (paallystysilmoitus-domain/laske-muutokset-kokonaishintaan
                    (:tyot paallystysilmoitus))
     :muokkaaja (:id kayttaja)
     :id kohde-id
     :urakka urakka-id}))

(defn luo-tai-paivita-paallystysilmoitus [db kayttaja urakka-id kohde-id
                                          {:keys [perustiedot] :as paallystysilmoitus}]
  (let [ilmoitustiedot-json (paallystysilmoitussanoma/rakenna paallystysilmoitus)
        paallystysilmoitus (if (q-paallystys/onko-paallystysilmoitus-olemassa-kohteelle? db {:id kohde-id})
                             (paivita-paallystysilmoitus db kayttaja urakka-id kohde-id paallystysilmoitus ilmoitustiedot-json)
                             (luo-paallystysilmoitus db kayttaja kohde-id paallystysilmoitus ilmoitustiedot-json))]
    (str (:id paallystysilmoitus))))

(defn pura-paallystysilmoitus [data]
  (-> (:paallystysilmoitus data)
      (assoc :alikohteet (mapv :alikohde (get-in data [:paallystysilmoitus :yllapitokohde :alikohteet])))
      (assoc :alustatoimenpiteet (mapv :alustatoimenpide (get-in data [:paallystysilmoitus :alustatoimenpiteet])))
      (assoc :tyot (mapv :tyo (get-in data [:paallystysilmoitus :tyot])))))

(defn validoi-paallystysilmoitus [db urakka-id kohde paallystysilmoitus]
  (validointi/tarkista-urakan-kohde db urakka-id (:id kohde))
  (let [kohteen-sijainti (get-in paallystysilmoitus [:yllapitokohde :sijainti])
        alikohteet (:alikohteet paallystysilmoitus)
        alustatoimenpiteet (:alustatoimenpiteet paallystysilmoitus)
        kohteen-tienumero (:tr-numero kohde)]
    (validointi/tarkista-paallystysilmoitus db (:id kohde) kohteen-tienumero kohteen-sijainti alikohteet alustatoimenpiteet)))

(defn tallenna-paallystysilmoitus [db kayttaja urakka-id kohde paallystysilmoitus]
  (let [kohteen-sijainti (get-in paallystysilmoitus [:yllapitokohde :sijainti])
        alikohteet (:alikohteet paallystysilmoitus)]
    (paivita-kohde db (:id kohde) kohteen-sijainti)
    (let [paivitetyt-alikohteet (paivita-alikohteet db kohde alikohteet)
          ;; Päivittyneiden alikohteiden id:t pitää päivittää päällystysilmoituksille
          paallystysilmoitus (assoc-in paallystysilmoitus [:yllapitokohde :alikohteet] paivitetyt-alikohteet)]
      (luo-tai-paivita-paallystysilmoitus db kayttaja urakka-id (:id kohde) paallystysilmoitus))))


(defn kirjaa-paallystysilmoitus [db kayttaja urakka-id kohde-id data]
  (jdbc/with-db-transaction
    [db db]
    (let [paallystysilmoitus (pura-paallystysilmoitus data)
          kohde (first (q-yllapitokohteet/hae-yllapitokohde db {:id kohde-id}))
          _ (validoi-paallystysilmoitus db urakka-id kohde paallystysilmoitus)
          id (tallenna-paallystysilmoitus db kayttaja urakka-id kohde paallystysilmoitus)]
      id)))
