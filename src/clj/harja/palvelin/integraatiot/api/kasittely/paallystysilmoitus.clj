(ns harja.palvelin.integraatiot.api.kasittely.paallystysilmoitus
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [dekoodaa-base64]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [tee-kirjausvastauksen-body]]
            [harja.kyselyt.yllapitokohteet :as q-yllapitokohteet]
            [harja.kyselyt.paallystys :as q-paallystys]
            [harja.kyselyt.tieverkko :as q-tieverkko]
            [harja.palvelin.integraatiot.api.sanomat.paallystysilmoitus :as paallystysilmoitussanoma]
            [clojure.java.jdbc :as jdbc])
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
            sijainti-geometria (:tierekisteriosoitteelle_viiva (first (q-tieverkko/tierekisteriosoite-viivaksi db osoite)))
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

(defn luo-tai-paivita-paallystysilmoitus [db kayttaja kohde-id paallystysilmoitus]
  (let [ilmoitustiedot (paallystysilmoitussanoma/rakenna paallystysilmoitus)
        paallystysilmoitus (if (q-paallystys/onko-paallystysilmoitus-olemassa-kohteelle? db {:id kohde-id})
                             (q-paallystys/paivita-paallystysilmoituksen-ilmoitustiedot<!
                               db
                               {:ilmoitustiedot ilmoitustiedot
                                :muokkaaja (:id kayttaja)
                                :id kohde-id})
                             (q-paallystys/luo-paallystysilmoitus<!
                               db
                               {:paallystyskohde kohde-id
                                :tila "aloitettu"
                                :ilmoitustiedot ilmoitustiedot
                                :aloituspvm nil
                                :valmispvm_kohde nil
                                :valmispvm_paallystys nil
                                :takuupvm nil
                                :muutoshinta nil
                                :kayttaja (:id kayttaja)}))]
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

(defn tallenna-paallystysilmoitus [db kayttaja kohde paallystysilmoitus]
  (let [kohteen-sijainti (get-in paallystysilmoitus [:yllapitokohde :sijainti])
        alikohteet (:alikohteet paallystysilmoitus)]
    (paivita-kohde db (:id kohde) kohteen-sijainti)
    (let [paivitetyt-alikohteet (paivita-alikohteet db kohde alikohteet)
          ;; Päivittyneiden alikohteiden id:t pitää päivittää päällystysilmoituksille
          paallystysilmoitus (assoc-in paallystysilmoitus [:yllapitokohde :alikohteet] paivitetyt-alikohteet)]
      (luo-tai-paivita-paallystysilmoitus db kayttaja (:id kohde) paallystysilmoitus))))


(defn kirjaa-paallystysilmoitus [db kayttaja urakka-id kohde-id data]
  (jdbc/with-db-transaction
    [db db]
    (let [paallystysilmoitus (pura-paallystysilmoitus data)
          kohde (first (q-yllapitokohteet/hae-yllapitokohde db {:id kohde-id}))
          _ (validoi-paallystysilmoitus db urakka-id kohde paallystysilmoitus)
          id (tallenna-paallystysilmoitus db kayttaja kohde paallystysilmoitus)]
      id)))