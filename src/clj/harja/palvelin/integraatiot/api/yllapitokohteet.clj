(ns harja.palvelin.integraatiot.api.yllapitokohteet
  "Ylläpitokohteiden hallinta"
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [dekoodaa-base64]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.sanomat.yllapitokohdesanomat :as yllapitokohdesanomat]
            [harja.kyselyt.yllapitokohteet :as q-yllapitokohteet]
            [harja.kyselyt.paallystys :as q-paallystys]
            [harja.kyselyt.tieverkko :as q-tieverkko]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.integraatiot.api.tyokalut.palvelut :as palvelut]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.integraatiot.api.sanomat.paallystysilmoitus :as paallystysilmoitus])
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

(defn paivita-paallystysilmoitus [db kayttaja kohde-id paallystysilmoitus]
  (let [ilmoitustiedot (paallystysilmoitus/rakenna paallystysilmoitus)
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

(defn validoi-paallystysilmoitus [db kayttaja urakka-id kohde paallystysilmoitus]
  (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
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
          paallystysilmoitus (assoc-in paallystysilmoitus [:yllapitokohde :alikohteet] paivitetyt-alikohteet)]
      (paivita-paallystysilmoitus db kayttaja (:id kohde) paallystysilmoitus))))

(defn kirjaa-paallystysilmoitus [db kayttaja {:keys [urakka-id kohde-id]} data]
  (log/debug (format "Kirjataan urakan (id: %s) kohteelle (id: %s) päällystysilmoitus" urakka-id kohde-id))
  (jdbc/with-db-transaction
    [db db]
    (let [urakka-id (Integer/parseInt urakka-id)
          kohde-id (Integer/parseInt kohde-id)
          paallystysilmoitus (pura-paallystysilmoitus data)
          kohde (first (q-yllapitokohteet/hae-yllapitokohde db {:id kohde-id}))
          _ (validoi-paallystysilmoitus db kayttaja urakka-id kohde paallystysilmoitus)
          id (tallenna-paallystysilmoitus db kayttaja kohde paallystysilmoitus)]
      (tee-kirjausvastauksen-body
        {:ilmoitukset (str "Päällystysilmoitus kirjattu onnistuneesti.")
         :id id}))))

(defn hae-yllapitokohteet [db parametit kayttaja]
  (let [urakka-id (Integer/parseInt (:id parametit))]
    (log/debug (format "Haetaan urakan (id: %s) ylläpitokohteet käyttäjälle: %s (id: %s)."
                       urakka-id
                       (:kayttajanimi kayttaja)
                       (:id kayttaja)))
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (let [yllapitokohteet (into []
                                (map konv/alaviiva->rakenne)
                                (q-yllapitokohteet/hae-urakan-yllapitokohteet-alikohteineen db {:urakka urakka-id}))
          yllapitokohteet (konv/sarakkeet-vektoriin
                            yllapitokohteet
                            {:kohdeosa :alikohteet}
                            :id)]
      (yllapitokohdesanomat/rakenna-kohteet yllapitokohteet))))

(def palvelut
  [{:palvelu :hae-yllapitokohteet
    :polku "/api/urakat/:id/yllapitokohteet"
    :tyyppi :GET
    :vastaus-skeema json-skeemat/urakan-yllapitokohteiden-haku-vastaus
    :kasittely-fn (fn [parametit _ kayttaja db] (hae-yllapitokohteet db parametit kayttaja))}
   {:palvelu :kirjaa-paallystysilmoitus
    :polku "/api/urakat/:urakka-id/yllapitokohteet/:kohde-id/paallystysilmoitus"
    :tyyppi :POST
    :kutsu-skeema json-skeemat/paallystysilmoituksen-kirjaus
    :vastaus-skeema json-skeemat/kirjausvastaus
    :kasittely-fn (fn [parametrit data kayttaja db] (kirjaa-paallystysilmoitus db kayttaja parametrit data))}])

(defrecord Yllapitokohteet []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (palvelut/julkaise http db integraatioloki palvelut)
    this)
  (stop [{http :http-palvelin :as this}]
    (palvelut/poista http palvelut)
    this))
