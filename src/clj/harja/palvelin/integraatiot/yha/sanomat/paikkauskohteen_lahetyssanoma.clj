(ns harja.palvelin.integraatiot.yha.sanomat.paikkauskohteen-lahetyssanoma
  (:require [harja.kyselyt.urakat :as q-urakka]
            [harja.kyselyt.paikkaus :as q-paikkaus]
            [harja.domain.paikkaus :as paikkaus]
            [harja.tyokalut.json-validointi :as json]
            [harja.tyokalut.yleiset :as yleiset]
            [harja.pvm :as pvm]
            [taoensso.timbre :as log]
            [cheshire.core :as cheshire]
            [namespacefy.core :refer [unnamespacefy]]
            [clojure.walk :refer [prewalk]]
            [clojure.set :refer [rename-keys]])
  (:use [slingshot.slingshot :only [throw+]]))

(def +paikkauksen-vienti+ "json/yha/paikkausten-vienti-request.schema.json")

(defn- parsi-tienkohdat [tienkohta]
  {::paikkaus/reunat (mapv #(into {:reuna %}) (::paikkaus/reunat tienkohta))
   ::paikkaus/ajouravalit (mapv #(into {:ajouravali %}) (::paikkaus/ajouravalit tienkohta))
   ::paikkaus/ajourat (mapv #(into {:ajoura %}) (::paikkaus/ajourat tienkohta))
   ::paikkaus/keskisaumat (mapv #(into {:keskisauma %}) (::paikkaus/keskisaumat tienkohta))})

(defn muodosta-sanoma-json
  "Muodostaa tietokannasta haetuista urakka-, paikkauskohde-, paikkaus- ja paikkauksen_materiaali-tiedoista
  paikkauksen-vienti-request-skeeman mukaisen sanoman."
  [urakka kohde paikkaukset]
  (let [urakka (prewalk #(if (map? %) (unnamespacefy %) %) urakka)
        paikkaukset (prewalk #(if (map? %) (unnamespacefy %) %) paikkaukset)
        kohde (prewalk #(if (map? %) (unnamespacefy %) %) kohde)
        urakka {:urakka (rename-keys urakka {:id :harja-id})}
        kasittele-materiaali (fn [m]
                               {:kivi-ja-sideaine (-> m
                                                      (dissoc :materiaali-id)
                                                      (rename-keys {:kuulamylly-arvo :km-arvo})
                                                      )})
        kasittele-paikkaus (fn [p]
                             {:paikkaus (-> p
                                          (dissoc :sijainti :urakka-id :paikkauskohde-id :ulkoinen-id :lahde)
                                          (rename-keys {:tierekisteriosoite :sijainti})
                                          (assoc :alkuaika (pvm/aika-yha-format (:alkuaika p)))
                                          (assoc :loppuaika (pvm/aika-yha-format (:loppuaika p)))
                                          (assoc :massamenekki (when (number? (:massamenekki p))
                                                                 (yleiset/round2 2 (:massamenekki p))))
                                          (conj {:kivi-ja-sideaineet (mapv kasittele-materiaali (:materiaalit p))})
                                          (dissoc :materiaalit))})
        kohteet {:paikkauskohteet [{:paikkauskohde (-> kohde
                                                       (dissoc :ulkoinen-id :yhalahetyksen-tila :yhalahetyksen-aika :virhe)
                                                       (rename-keys {:id :harja-id})
                                                       (conj {:paikkaukset (mapv kasittele-paikkaus paikkaukset)}))}]}
        sanomasisalto (merge urakka kohteet)]
    (cheshire/encode sanomasisalto)))

(defn muodosta [db urakka-id kohde-id]
  (let [urakka (select-keys (first (q-urakka/hae-urakan-nimi db {:urakka urakka-id})) [:id :nimi])
        kohde (first (q-paikkaus/hae-paikkauskohteet db {::paikkaus/id kohde-id
                                                         :harja.domain.paikkaus/urakka-id urakka-id
                                                         :harja.domain.muokkaustiedot/poistettu? false}))
        kohde (dissoc kohde :harja.domain.muokkaustiedot/luotu
                      :harja.domain.muokkaustiedot/muokattu
                      ::paikkaus/urakka-id
                      ::paikkaus/alkupvm
                      ::paikkaus/loppupvm
                      ::paikkaus/paikkauskohteen-tila
                      ::paikkaus/yksikko
                      ::paikkaus/tyomenetelma
                      ::paikkaus/tarkistettu
                      ::paikkaus/tarkistaja-id
                      ::paikkaus/ilmoitettu-virhe)
        tyomenetelman-lyhenne (into {}
                                    (map (fn [{:keys [id lyhenne]}]
                                           {id lyhenne})
                                         (q-paikkaus/hae-paikkauskohteen-tyomenetelmien-lyhenteet db)))
        paikkaukset (q-paikkaus/hae-paikkaukset-materiaalit db {::paikkaus/paikkauskohde-id kohde-id
                                                                ::paikkaus/urakka-id urakka-id
                                                                :harja.domain.muokkaustiedot/poistettu? false})
        paikkaukset (map
                      #(let [tienkohdat (first
                                          (q-paikkaus/hae-paikkauksen-tienkohdat db
                                                                                 {::paikkaus/paikkaus-id (::paikkaus/id %)}))
                             tienkohdat-parsittu (parsi-tienkohdat tienkohdat)]
                         (-> %
                             ;; YHA:n API haluaa merkkijonon eikä integeriä
                             (assoc ::paikkaus/tyomenetelma (tyomenetelman-lyhenne (::paikkaus/tyomenetelma %)))
                             (assoc-in [::paikkaus/tierekisteriosoite :ajorata] (::paikkaus/ajorata tienkohdat))
                             (assoc-in [::paikkaus/tierekisteriosoite :tienkohdat] tienkohdat-parsittu)))
                      paikkaukset)
        json (muodosta-sanoma-json urakka kohde paikkaukset)]

    ;; Huom! draft 4 json validointi poikkeuksellisesti
    (if-let [virheet (json/validoi +paikkauksen-vienti+ json false)]
      (let [virheviesti (format "Kohdetta ei voi lähettää YHAan. JSON ei ole validi. Validointivirheet: %s" virheet)]
        (log/error virheviesti)
        (throw+ {:type :invalidi-yha-paikkaus-json
                 :error virheviesti}))
      json)))


