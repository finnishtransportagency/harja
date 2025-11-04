(ns harja.kyselyt.urakat
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/urakat.sql"
  {:positional? true})

(declare urakan-paasopimus-id hae-urakka hae-urakan-tiedot hae-urakan-tyyppi hae-urakan-sopimukset
  hae-urakan-sampo-id hae-yksittainen-urakka hae-urakan-ely hae-urakan-parametrit aseta-tai-paivita-urakkaparametrit
  hae-urakat-tyypilla-ja-hallintayksikolla urakan-hallintayksikko hae-id-sampoidlla aseta-urakan-toimenkuvat
  hae-urakan-alkuvuosi onko-olemassa onko-urakalla-tehtavaa hae-urakka-sijainnilla listaa-kaikki-urakat-analytiikalle
  listaa-urakat-analytiikalle-hoitovuosittain hae-paallystysurakat-analytiikalle
  hae-urakkatiedot-laskutusyhteenvetoon)

(defn onko-olemassa? [db id]
  (:exists (first (onko-olemassa db id))))
