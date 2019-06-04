(ns harja.domain.tiemerkinta-toteumat
  "Tiemerkinnän toteumien spec-määritykset"
  (:require
    [clojure.spec.alpha :as s]
    [harja.pvm :as pvm]
    [harja.domain.urakka :as urakka]
    [harja.domain.yllapitokohde :as yllapitokohteet]
    [harja.domain.tierekisteri :as tr-domain]
    [harja.tyokalut.spec-apurit :as apurit]))

;; Toteuma

(s/def ::selite string?)
(s/def ::paivamaara inst?)
(s/def ::hintatyyppi #{:toteuma :suunnitelma})
(s/def ::yllapitoluokka (s/nilable ::yllapitokohteet/yllapitoluokka)) ;; nil = ei ylläpitoluokkaa
(s/def ::id (s/nilable int?))
(s/def ::pituus ::tr-domain/pituus)
(s/def ::hinta-kohteelle (s/and string? #(>= (count %) 1)))
(s/def ::yllapitokohde-id ::apurit/postgres-serial)
(s/def ::tr-numero ::tr-domain/numero)
(s/def ::hinta (s/and number? (comp not neg?) #(< % 10000000)))
(s/def ::poistettu boolean?)
(s/def ::vuosi int?)

(s/def ::tiemerkinnan-yksikkohintainen-tyo
  (s/keys :req-un [::id ::selite ::hintatyyppi ::hinta
                   ;; Joko linkitetty ylläpitokohteeseen ja määritelty hinta-kohteelle -avain,
                   ;; tai sitten on kohteeseen kuulumaton toteuma
                   (or (and ::yllapitokohde-id ::hinta-kohteelle)
                       (and ::tr-numero ::pituus))]
          :opt-un [::poistettu ::paivamaara ::yllapitoluokka]))

;; Haut

(s/def ::urakka-id ::urakka/id)

(s/def ::hae-tiemerkinnan-yksikkohintaiset-tyot-kysely
  (s/keys :req-un [::urakka-id ::vuosi]))

(s/def ::hae-tiemerkinnan-yksikkohintaiset-tyot-vastaus
  (s/coll-of ::tiemerkinnan-yksikkohintainen-tyo))

;; Tallennukset

(s/def ::toteumat (s/coll-of ::tiemerkinnan-yksikkohintainen-tyo))
(s/def ::tallenna-tiemerkinnan-yksikkohintaiset-tyot-kysely
  (s/keys :req-un [::urakka-id ::vuosi ::toteumat]))

(s/def ::tallenna-tiemerkinnan-yksikkohintaiset-tyot-vastaus
  ::hae-tiemerkinnan-yksikkohintaiset-tyot-vastaus)

(defn maarittele-hinnan-kohde
  "Palauttaa stringin, jossa on ylläpitokohteen tieosoitteen tiedot. Käytetään tunnistamaan tilanne,
   jossa ylläpitokohteeseen liittyvä hinta on annettu ylläpitokohteen vanhalle tieosoitteelle."
  [{:keys [tr-numero tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys] :as kohde}]
  (assert (and tr-numero tr-alkuosa tr-alkuetaisyys) "Puutteelliset parametrit")
  ;; Tod.näk. et halua muuttaa tätä ainakaan migratoimatta kannassa olevaa dataa.
  (str tr-numero " / " tr-alkuosa " / " tr-alkuetaisyys " / " tr-loppuosa " / " tr-loppuetaisyys))
