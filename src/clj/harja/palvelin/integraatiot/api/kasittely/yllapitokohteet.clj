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

;; Aiemmin pääkohteella oli pakko olla ajorata ja kaista. Näitä ei voinut muokata, eli API:sta pystyi
;; päivittämään vain arvot: aosa, aet, losa, let. Kohteen alikohteet katsottiin olevan samalla ajoradalla
;; ja kaistalla pääkohteen kanssa.
;; Jatkossa (kaudella 2018) pääkohteen ajorataa ja kaistaa ei ole pakko olla, vaan ne voidaan antaa alikohdetasolla.
;; Pääkohteen ajorataa ja kaistaa voidaan myös päivittää API:sta.
;; Taaksepäinyhteensopivuuden vuoksi, mikäli pääkohdetta tai alikohdetta päivitetään ilman ajorataa ja kaistaa,
;; käytetään kannassa olevaa pääkohteen ajorataa ja kaistaa, mikäli sellaiset löytyy.

(defn paivita-alikohteet [db kohde alikohteet]
  (q-yllapitokohteet/poista-yllapitokohteen-kohdeosat! db {:id (:id kohde)})

  (mapv
    (fn [alikohde]
      (let [sijainti (:sijainti alikohde)
            paivityksessa-alikohteella-ajorata-ja-kaista? (boolean (and (:ajr sijainti) (:kaista sijainti)))
            olemassa-oleva-paakohde (q-yllapitokohteet/hae-yllapitokohde db {:id (:id kohde)})
            paakohteella-ajorata-ja-kaista? (boolean (and (:tr_ajorata olemassa-oleva-paakohde)
                                                          (:tr_kaista olemassa-oleva-paakohde)))
            alikohde (if (and paakohteella-ajorata-ja-kaista?
                              (not paivityksessa-alikohteella-ajorata-ja-kaista?))
                       (assoc alikohde
                         :ajr (:tr_ajorata olemassa-oleva-paakohde)
                         :kaista (:tr_kaista olemassa-oleva-paakohde))
                       alikohde)
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
  (let [paivityksessa-ajorata-ja-kaista? (boolean (and (:ajr kohteen-sijainti) (:kaista kohteen-sijainti)))
        olemassa-oleva (q-yllapitokohteet/hae-yllapitokohde db {:id kohde-id})
        olemassa-oleva-ajorata-ja-kaista? (boolean (and (:tr_ajorata olemassa-oleva) (:tr_kaista olemassa-oleva)))
        kohteen-sijainti (if (and olemassa-oleva-ajorata-ja-kaista?
                                  (not paivityksessa-ajorata-ja-kaista?))
                           (assoc kohteen-sijainti
                             :ajr (:tr_ajorata olemassa-oleva)
                             :kaista (:tr_kaista olemassa-oleva))
                           kohteen-sijainti)]

    (q-yllapitokohteet/paivita-yllapitokohteen-sijainti!
      db (assoc (clojure.set/rename-keys
                  kohteen-sijainti
                  {:aosa :tr_alkuosa
                   :aet :tr_alkuetaisyys
                   :losa :tr_loppuosa
                   :let :tr_loppuetaisyys
                   :ajr :tr_ajorata
                   :kaista :tr_kaista})
           :id
           kohde-id))))