import CertifiedAtc.GreenfieldCommunicationsDeliveredCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldCommunicationsExpandedCurrentShape` packages the broadened
communications/surveillance branch behind one current-shape theorem boundary.

This closes the current explicit model for:

- single-step radio instructions
- single-step transponder/surveillance instructions
- the delivered immediate radio/transponder compound layer
- the current lifecycle and supersession consequences already proved in
  `GreenfieldCommunicationsCompound`

This is still intentionally current-shape:

- no new world-resolution theory
- no broader coordination/jurisdiction semantics
- no richer surveillance automation than the current runtime surface
-/

abbrev GreenfieldCommunicationsExpandedCurrentShapeIssuable :=
  GreenfieldCommunicationsDeliveredCurrentShapeIssuable

abbrev GreenfieldCommunicationsExpandedCurrentShapeWorldAuthorized :=
  GreenfieldCommunicationsDeliveredCurrentShapeWorldAuthorized

abbrev GreenfieldCommunicationsExpandedCurrentShapeReachableIssuanceTheorem :=
  @GreenfieldCommunicationsDeliveredCurrentShapeReachableIssuanceTheorem

abbrev GreenfieldCommunicationsExpandedCurrentShapeAuthorizedIssuanceTheorem :=
  @GreenfieldCommunicationsDeliveredCurrentShapeAuthorizedIssuanceTheorem

end Greenfield
end CertifiedAtc
