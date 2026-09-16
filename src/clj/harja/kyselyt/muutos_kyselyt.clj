(ns harja.kyselyt.muutos-kyselyt
  (:require [jeesql.core :refer [defqueries]]))


(defqueries "harja/kyselyt/muutos_kyselyt.sql")

(declare hae-urakan-hoitovuoden-kirjatut-muutokset rahavarausten-toteumat rahavarausmuutosten-syyt
  hae-laskutusrajan-muutosten-summa-hoitovuodelle
  hae-tehtava-maaramuutokset paivita-tehtava-tiedot<! paivita-muutostyo-kulukohdistus!
  hae-pysyvan-muutoksen-kustannustiedot hae-johto-ja-hallintokorvausmuutoksen-tiedot
  luo-jjh-kulun-kohdistus<! paivita-muutos-kulu-linkitys! luo-muutos-kulu-linkitys<!
  hae-muutoksen-liite-idt poista-muutos-liite-linkitys! linkita-muutos-ja-liite<!
  luo-tai-paivita-erillisrahoitettu-kustannusvaikutus<! luo-tai-paivita-muutos-kustannusvaikutus<!
  poista-tehtavan-maaramuutos! luo-tai-paivita-tehtavan-maaramuutos<! muutostyolle-jo-kirjatut-kulut-yhteensa
  onko-muutoksella-kuluja-ennen-voimassa-paivaa? hae-muutos paivita-muutos<! luo-muutos<!
  hae-muutostyon-kulujen-maara hae-jjh-muutoksen-kulut poista-muutos! hae-urakan-muutostyot
  upsert-rahavarausmuutosten-syyt!)
