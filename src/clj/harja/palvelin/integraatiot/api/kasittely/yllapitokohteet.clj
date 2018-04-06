(ns harja.palvelin.integraatiot.api.kasittely.yllapitokohteet
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [dekoodaa-base64]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [tee-kirjausvastauksen-body]]
            [harja.kyselyt.yllapitokohteet :as q-yllapitokohteet]
            [taoensso.timbre :as log]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
            [harja.domain.paallystysilmoitus :as paallystysilmoitus])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(defn paivita-alikohteet [db kohde alikohteet]
  (q-yllapitokohteet/poista-yllapitokohteen-kohdeosat! db {:id (:id kohde)})

  (mapv
    (fn [alikohde]
      (let [sijainti (:sijainti alikohde)
            paivityksessa-alikohteella-ajorata-ja-kaista? (boolean (and (:ajr sijainti) (:kaista sijainti)))
            olemassa-oleva-paakohde (first (q-yllapitokohteet/hae-yllapitokohde db {:id (:id kohde)}))
            paakohteella-ajorata-ja-kaista? (boolean (and (:tr-ajorata olemassa-oleva-paakohde)
                                                          (:tr-kaista olemassa-oleva-paakohde)))
            ;; Aiemmin pääkohteella oli pakko olla ajorata ja kaista, ja alikohteen katsottiin kuuluvan
            ;; samalle ajoradalle ja kaistalle.
            ;; Jatkossa (kaudella 2018) pääkohteella ei ole pakko olla ajorataa ja kaistaa, vaan ne voidaan
            ;; antaa alikohdetasolla. Taaksepäinyhteensopivuuden vuoksi tehdään niin, että mikäli
            ;; päivitetään alikohde ilman ajorataa ja kaistaa, asetetaan ne pääkohteelta, jos ne on.
            sijainti (if (and paakohteella-ajorata-ja-kaista?
                              (not paivityksessa-alikohteella-ajorata-ja-kaista?))
                       (assoc sijainti
                         :ajr (:tr-ajorata olemassa-oleva-paakohde)
                         :kaista (:tr-kaista olemassa-oleva-paakohde))
                       sijainti)
            parametrit {:yllapitokohde (:id kohde)
                        :nimi (:nimi alikohde)
                        :tunnus (:tunnus alikohde)
                        :tr_numero (:numero sijainti)
                        :tr_alkuosa (:aosa sijainti)
                        :tr_alkuetaisyys (:aet sijainti)
                        :tr_loppuosa (:losa sijainti)
                        :tr_loppuetaisyys (:let sijainti)
                        :tr_ajorata (:ajr sijainti)
                        :tr_kaista (:kaista sijainti)
                        :paallystetyyppi (paallystys-ja-paikkaus/hae-koodi-apin-paallysteella (:paallystetyyppi alikohde))
                        :raekoko (:raekoko alikohde)
                        :tyomenetelma (paallystysilmoitus/tyomenetelman-koodi-nimella (:tyomenetelma alikohde))
                        :massamaara (:kokonaismassamaara alikohde)
                        :toimenpide (:toimenpide alikohde)
                        :ulkoinen-id (:ulkoinen-id alikohde)}]
        (assoc alikohde :id (:id (q-yllapitokohteet/luo-yllapitokohdeosa<! db parametrit)))))
    alikohteet))

(defn paivita-alikohteet-paallystysilmoituksesta [db kohde alikohteet]
  (q-yllapitokohteet/poista-yllapitokohteen-kohdeosat! db {:id (:id kohde)})
  (mapv
    (fn [alikohde]
      (let [sijainti (:sijainti alikohde)
            parametrit {:yllapitokohde (:id kohde)
                        :nimi (:nimi alikohde)
                        :tunnus (:tunnus alikohde)
                        :tr_numero (:numero sijainti)
                        :tr_alkuosa (:aosa sijainti)
                        :tr_alkuetaisyys (:aet sijainti)
                        :tr_loppuosa (:losa sijainti)
                        :tr_loppuetaisyys (:let sijainti)
                        :tr_ajorata (:ajr sijainti)
                        :tr_kaista (:kaista sijainti)
                        :ulkoinen-id (:ulkoinen-id alikohde)}]
        (assoc alikohde :id (:id (q-yllapitokohteet/luo-yllapitokohdeosa-paallystysilmoituksen-apista<!
                                   db parametrit)))))
    alikohteet))

(defn paivita-kohde [db kohde-id kohteen-sijainti]
  (q-yllapitokohteet/paivita-yllapitokohteen-sijainti!
    db (assoc (clojure.set/rename-keys
                kohteen-sijainti
                ;; Huom: Ajorataa ja kaistaa ei saa koskaan päivittää API:sta!
                {:aosa :tr_alkuosa
                 :aet :tr_alkuetaisyys
                 :losa :tr_loppuosa
                 :let :tr_loppuetaisyys})
         :id
         kohde-id)))