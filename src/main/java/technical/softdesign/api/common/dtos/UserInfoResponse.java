package technical.softdesign.api.common.dtos;

import static technical.softdesign.api.common.dtos.UserInfoResponse.Status.ABLE_TO_VOTE;

public record UserInfoResponse(
        Status status
) {

    public enum Status {
        ABLE_TO_VOTE,
        UNABLE_TO_VOTE
    }

    public boolean canVote() {
        return status.equals(ABLE_TO_VOTE);
    }
}